package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayFinalizationAttemptRepository;
import com.nemonicworld.relay.repository.RelayFinalizationRetryRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * FINALIZING 방의 canvasIndex별 최종 이미지를 만들고 artifact/gallery 저장 후 방을 FINISHED로
 * 전환합니다.
 */
@Service
public class RelayRoomFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomFinalizationService.class);
    private static final String FINALIZATION_STATE_ERROR_MESSAGE = "릴레이 최종화 상태가 올바르지 않습니다.";

    private final RelayRoomRepository relayRoomRepository;
    private final RelayArtifactRepository relayArtifactRepository;
    private final RelayResultStorage relayResultStorage;
    private final RelayFinalizationArtifactCreator relayFinalizationArtifactCreator;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayFinalizationRetryRepository relayFinalizationRetryRepository;
    private final RelayFinalizationAttemptRepository relayFinalizationAttemptRepository;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final ThreadLocal<RelayFinalizationFailureContext> failureContext = new ThreadLocal<>();
    private final ThreadLocal<RelayFinalizationAttempt> activeAttempt = new ThreadLocal<>();
    private final int scanLimit;
    private final Duration lockTtl;
    private final Duration attemptTtl;
    private final Duration finalizationReadyDelay;
    private final int maxRetryCount;

    public RelayRoomFinalizationService(RelayRoomRepository relayRoomRepository,
        RelayArtifactRepository relayArtifactRepository, RelayResultStorage relayResultStorage,
        RelayFinalizationArtifactCreator relayFinalizationArtifactCreator,
        RelayRoomEventPublisher relayRoomEventPublisher, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayFinalizationRetryRepository relayFinalizationRetryRepository,
        RelayFinalizationAttemptRepository relayFinalizationAttemptRepository,
        RelayRoomCloseCommand relayRoomCloseCommand,
        @Value("${nemonic.relay.finalization.scan-limit:50}") int scanLimit,
        @Value("${nemonic.relay.finalization.lock-ttl-seconds:120}") long lockTtlSeconds,
        @Value("${nemonic.relay.finalization.attempt-ttl-hours:24}") long attemptTtlHours,
        @Value("${nemonic.relay.finalization.ready-delay-ms:1000}") long readyDelayMs,
        @Value("${nemonic.relay.finalization.max-retry-count:60}") int maxRetryCount) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayResultStorage = relayResultStorage;
        this.relayFinalizationArtifactCreator = relayFinalizationArtifactCreator;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayFinalizationRetryRepository = relayFinalizationRetryRepository;
        this.relayFinalizationAttemptRepository = relayFinalizationAttemptRepository;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.scanLimit = scanLimit;
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        this.attemptTtl = Duration.ofHours(Math.max(1L, attemptTtlHours));
        this.finalizationReadyDelay = Duration.ofMillis(Math.max(0L, readyDelayMs));
        this.maxRetryCount = Math.max(1, maxRetryCount);
    }

    /**
     * 스케줄러가 찾은 FINALIZING 방들을 순회하며 최종화를 시도합니다.
     */
    public RelayFinalizationProcessResult processFinalizingRooms() {
        List<RelayRoomState> finalizingRooms = relayRoomRepository.findFinalizingRooms(scanLimit);
        LocalDateTime readyCutoff = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minus(finalizationReadyDelay);
        int processedRoomCount = 0;
        int resultCount = 0;

        for (RelayRoomState finalizingRoom : finalizingRooms) {
            if (!isReadyForFinalization(finalizingRoom, readyCutoff)) {
                continue;
            }

            try {
                RelayRoomFinalizationResult result = processFinalizingRoom(finalizingRoom.roomCode());
                if (result.processed()) {
                    processedRoomCount++;
                    resultCount += result.resultCount();
                }
            } catch (RuntimeException e) {
                handleFinalizationFailure(finalizingRoom.roomCode(), e);
                log.warn("릴레이 최종 결과물 생성 중 오류가 발생했습니다. roomCode={}", finalizingRoom.roomCode(), e);
            }
        }

        return new RelayFinalizationProcessResult(finalizingRooms.size(), processedRoomCount, resultCount);
    }

    public RelayRoomFinalizationResult triggerFinalization(String roomCode) {
        RelayRoomEventLogger.apiBusiness("relay_finalization_immediate_triggered",
            metadata("room_id", roomCode, "trigger_reason", "all_parts_completed"));
        try {
            return processFinalizingRoom(roomCode);
        } catch (RuntimeException e) {
            handleFinalizationFailure(roomCode, e);
            log.warn("릴레이 최종 결과물 즉시 생성 중 오류가 발생했습니다. roomCode={}", roomCode, e);
            return RelayRoomFinalizationResult.noOp(roomCode);
        }
    }

    private boolean isReadyForFinalization(RelayRoomState roomState, LocalDateTime readyCutoff) {
        return roomState.updatedAt() == null || !roomState.updatedAt().isAfter(readyCutoff);
    }

    private void handleFinalizationFailure(String roomCode, RuntimeException error) {
        RelayFinalizationFailureContext context = failureContext.get();
        failureContext.remove();

        int retryCount = relayFinalizationRetryRepository.incrementFailureCount(roomCode,
            RelayRoomRepository.ROOM_STATE_TTL);
        String stage = context == null ? "process" : context.stage();
        UUID artifactId = context == null ? null : context.artifactId();
        String attemptId = context == null ? null : context.attemptId();
        RelayRoomEventLogger.apiWarn("relay_finalization_attempt_failed", "failed relay finalization attempt",
            metadata("room_id", roomCode, "attempt_id", attemptId, "retry_count", retryCount, "stage", stage, "error",
                error.getClass().getSimpleName()),
            error);
        RelayRoomEventLogger.apiWarn("relay_finalization_failed", "failed to finalize relay room",
            metadata("room_id", roomCode, "stage", stage, "artifact_id", artifactId, "attempt_id", attemptId,
                "operation", "finalization", "retry_count", retryCount, "max_retry_count", maxRetryCount),
            error);

        if (retryCount >= maxRetryCount) {
            closeFinalizationFailedRoom(roomCode, retryCount);
        }
    }

    private void closeFinalizationFailedRoom(String roomCode, int retryCount) {
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINALIZING) {
            return;
        }

        RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
        if (!closeResult.closed()) {
            return;
        }

        RelayRoomEventLogger.apiBusiness("relay_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", "finalization_failed", "room_status_before",
                roomState.status(), "participant_count", roomState.participantCount(), "retry_count", retryCount,
                "closed_at", closeResult.closedAt()));
        relayRoomEventPublisher.publishRoomClosed(roomCode, closeResult.closedAt(), "finalization_failed");
    }

    /**
     * 한 방에 대한 최종화 lock을 획득한 뒤 실제 최종화 처리를 실행합니다.
     */
    public RelayRoomFinalizationResult processFinalizingRoom(String roomCode) {
        failureContext.remove();
        activeAttempt.remove();
        String lockToken = createFinalizationLockToken(roomCode);
        if (!relayRoomRepository.acquireFinalizationLock(roomCode, lockToken, lockTtl)) {
            RelayRoomEventLogger.apiBusiness("relay_finalization_lock_skipped",
                metadata("room_id", roomCode, "reason", "lock_not_acquired"));
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        String attemptId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayFinalizationAttempt attempt = RelayFinalizationAttempt.start(roomCode, attemptId, startedAt);

        try {
            saveActiveAttempt(attempt);
            RelayRoomEventLogger.apiBusiness("relay_finalization_attempt_started",
                metadata("room_id", roomCode, "attempt_id", attemptId, "retry_count",
                    relayFinalizationRetryRepository.getFailureCount(roomCode), "max_retry_count", maxRetryCount));
            return processLockedFinalizingRoom(roomCode);
        } finally {
            clearActiveAttempt(roomCode, attemptId);
            relayRoomRepository.releaseFinalizationLock(roomCode, lockToken);
        }
    }

    private String createFinalizationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=finalization,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    private void saveActiveAttempt(RelayFinalizationAttempt attempt) {
        activeAttempt.set(attempt);
        relayFinalizationAttemptRepository.save(attempt, attemptTtl);
    }

    private void clearActiveAttempt(String roomCode, String attemptId) {
        activeAttempt.remove();
        try {
            relayFinalizationAttemptRepository.clear(roomCode, attemptId);
        } catch (RuntimeException e) {
            RelayRoomEventLogger.apiWarn("relay_finalization_attempt_clear_failed",
                "failed to clear relay finalization attempt marker",
                metadata("room_id", roomCode, "attempt_id", attemptId), e);
        }
    }

    /**
     * 최신 Redis 상태를 기준으로 결과물을 만들고 방 상태를 FINISHED로 전환합니다.
     */
    private RelayRoomFinalizationResult processLockedFinalizingRoom(String roomCode) {
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINALIZING) {
            relayFinalizationRetryRepository.clearFailureCount(roomCode);
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long startedNanos = System.nanoTime();
        List<Integer> canvasIndexes = findCanvasIndexes(roomState);
        if (canvasIndexes.isEmpty()) {
            InternalServerException error = new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
            logFinalizationFailure(roomCode, "process", null, error);
            throw error;
        }

        List<RelayFinalizationArtifactResult> existingArtifacts = relayArtifactRepository
            .findRelayArtifactsBySourceRoomId(roomCode);
        ResolvedRelayFinalizationArtifacts resolvedArtifacts = resolveArtifacts(roomState, canvasIndexes,
            existingArtifacts, now);
        List<RelayFinalizationArtifactResult> artifacts = resolvedArtifacts.artifacts();
        RelayRoomState finishedRoomState = roomState.finish(now);
        if (!relayRoomRepository.saveIfUnchanged(roomState, finishedRoomState)) {
            ConflictException error = new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
            logFinalizationFailure(roomCode, "redis_update",
                artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), error);
            throw error;
        }
        relayInviteMetadataSyncService.syncWithRoomState(finishedRoomState);

        RelayRoomFinalizationResult result = RelayRoomFinalizationResult.finished(roomCode, artifacts, now);
        relayFinalizationRetryRepository.clearFailureCount(roomCode);
        relayRoomEventPublisher.publishResultCreated(result);
        if (resolvedArtifacts.recovered()) {
            RelayRoomEventLogger.websocketBusiness("relay_finalization_recovered",
                metadata("room_id", roomCode, "attempt_id", currentAttemptId(), "recovery_reason",
                    "existing_result_found", "result_count", result.resultCount()));
        } else {
            RelayRoomEventLogger.websocketBusiness("relay_result_created",
                metadata("room_id", roomCode, "attempt_id", currentAttemptId(), "result_count", result.resultCount(),
                    "artifact_ids", artifacts.stream().map(artifact -> artifact.artifactId().toString()).toList(),
                    "duration_ms", Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()));
        }

        return result;
    }

    /**
     * 이미 생성된 결과물이 있으면 재사용하고, 없으면 새로 합성해 DB에 저장합니다.
     */
    private ResolvedRelayFinalizationArtifacts resolveArtifacts(RelayRoomState roomState, List<Integer> canvasIndexes,
        List<RelayFinalizationArtifactResult> existingArtifacts, LocalDateTime now) {
        if (existingArtifacts.isEmpty()) {
            List<RelayFinalizationArtifactResult> artifacts;
            try {
                artifacts = relayFinalizationArtifactCreator.createAndUploadResults(roomState, canvasIndexes,
                    this::registerAttemptObjectKey, (stage, artifactId,
                        error) -> logFinalizationFailure(roomState.roomCode(), stage, artifactId, error));
            } catch (RuntimeException e) {
                cleanupCurrentAttemptResultObjects(roomState.roomCode());
                throw e;
            }
            try {
                relayArtifactRepository.saveRelayDrawingResults(roomState.roomCode(), artifacts,
                    findParticipantUuidValues(roomState), now);
                return new ResolvedRelayFinalizationArtifacts(artifacts, false);
            } catch (RuntimeException e) {
                logFinalizationFailure(roomState.roomCode(), "db_save",
                    artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), e);
                cleanupCurrentAttemptResultObjects(roomState.roomCode());
                throw e;
            }
        }

        if (matchesExpectedCanvasIndexes(existingArtifacts, canvasIndexes)) {
            return new ResolvedRelayFinalizationArtifacts(existingArtifacts, true);
        }

        InternalServerException error = new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
        logFinalizationFailure(roomState.roomCode(), "process", null, error);
        throw error;
    }

    private void logFinalizationFailure(String roomCode, String stage, UUID artifactId, RuntimeException error) {
        failureContext.set(new RelayFinalizationFailureContext(stage, artifactId, currentAttemptId()));
    }

    private void registerAttemptObjectKey(String objectKey) {
        RelayFinalizationAttempt attempt = activeAttempt.get();
        if (attempt == null || !StringUtils.hasText(objectKey)) {
            return;
        }

        saveActiveAttempt(attempt.addObjectKey(objectKey));
    }

    private String currentAttemptId() {
        RelayFinalizationAttempt attempt = activeAttempt.get();

        return attempt == null ? null : attempt.attemptId();
    }

    private void cleanupCurrentAttemptResultObjects(String roomCode) {
        RelayFinalizationAttempt attempt = activeAttempt.get();
        List<String> objectKeys = attempt == null
            ? List.of()
            : attempt.objectKeys().stream().filter(StringUtils::hasText).distinct().toList();
        if (objectKeys.isEmpty()) {
            return;
        }

        List<String> failedObjectKeys = new ArrayList<>();
        int deletedObjectCount = 0;
        for (String objectKey : objectKeys) {
            try {
                relayResultStorage.delete(objectKey);
                deletedObjectCount++;
            } catch (RuntimeException e) {
                failedObjectKeys.add(objectKey);
                RelayRoomEventLogger.apiWarn("relay_result_orphan_cleanup_failed",
                    "failed to clean orphan relay result object",
                    metadata("room_id", roomCode, "attempt_id", attempt.attemptId(), "object_key_hashes",
                        List.of(RelayRoomEventLogger.hash(objectKey)), "failed_object_count", 1, "error",
                        e.getClass().getSimpleName()),
                    e);
            }
        }

        RelayRoomEventLogger.apiBusiness("relay_result_orphan_cleanup_completed",
            metadata("room_id", roomCode, "attempt_id", attempt.attemptId(), "object_key_hashes",
                objectKeys.stream().map(RelayRoomEventLogger::hash).toList(), "deleted_object_count",
                deletedObjectCount, "failed_object_count", failedObjectKeys.size(), "result",
                failedObjectKeys.isEmpty() ? "success" : "partial_failure"));
    }

    /**
     * 최종 결과물을 만들어야 하는 canvasIndex 목록을 추출합니다.
     */
    private List<Integer> findCanvasIndexes(RelayRoomState roomState) {
        return roomState.assignments().stream().map(RelayRoomAssignment::canvasIndex).distinct().sorted().toList();
    }

    /**
     * 갤러리 지급 대상인 참여자 UUID 목록을 중복 없이 추출합니다.
     */
    private List<String> findParticipantUuidValues(RelayRoomState roomState) {
        return roomState.participants().stream().filter(participant -> !participant.dropped())
            .map(RelayRoomParticipant::userUuid).distinct().toList();
    }

    /**
     * 기존 결과물이 현재 방의 canvasIndex 개수와 정확히 맞는지 확인합니다.
     */
    private boolean matchesExpectedCanvasIndexes(List<RelayFinalizationArtifactResult> existingArtifacts,
        List<Integer> canvasIndexes) {
        List<Integer> existingCanvasIndexes = existingArtifacts.stream()
            .map(RelayFinalizationArtifactResult::canvasIndex).distinct().sorted().toList();

        return existingArtifacts.size() == canvasIndexes.size() && existingCanvasIndexes.equals(canvasIndexes);
    }

    private record ResolvedRelayFinalizationArtifacts(List<RelayFinalizationArtifactResult> artifacts,
        boolean recovered) {
    }

    private record RelayFinalizationFailureContext(String stage, UUID artifactId, String attemptId) {
    }
}
