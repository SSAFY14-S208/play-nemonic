package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.close.RelayRoomCloseCommand;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    private final RelayFinalizationArtifactCreator relayFinalizationArtifactCreator;
    private final RelayFinalizationAttemptSupport relayFinalizationAttemptSupport;
    private final RelayFinalizationFailureSupport relayFinalizationFailureSupport;
    private final RelayFinalizationCleanupSupport relayFinalizationCleanupSupport;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final int scanLimit;
    private final Duration lockTtl;
    private final Duration finalizationReadyDelay;
    private final int maxRetryCount;

    public RelayRoomFinalizationService(RelayRoomRepository relayRoomRepository,
        RelayArtifactRepository relayArtifactRepository,
        RelayFinalizationArtifactCreator relayFinalizationArtifactCreator,
        RelayFinalizationAttemptSupport relayFinalizationAttemptSupport,
        RelayFinalizationFailureSupport relayFinalizationFailureSupport,
        RelayFinalizationCleanupSupport relayFinalizationCleanupSupport,
        RelayRoomEventPublisher relayRoomEventPublisher, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRoomCloseCommand relayRoomCloseCommand,
        @Value("${nemonic.relay.finalization.scan-limit:50}") int scanLimit,
        @Value("${nemonic.relay.finalization.lock-ttl-seconds:120}") long lockTtlSeconds,
        @Value("${nemonic.relay.finalization.ready-delay-ms:1000}") long readyDelayMs,
        @Value("${nemonic.relay.finalization.max-retry-count:60}") int maxRetryCount) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayFinalizationArtifactCreator = relayFinalizationArtifactCreator;
        this.relayFinalizationAttemptSupport = relayFinalizationAttemptSupport;
        this.relayFinalizationFailureSupport = relayFinalizationFailureSupport;
        this.relayFinalizationCleanupSupport = relayFinalizationCleanupSupport;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.scanLimit = scanLimit;
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
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
        RelayFinalizationFailureSupport.RelayFinalizationFailureResult failureResult = relayFinalizationFailureSupport
            .handleFailure(roomCode, error, maxRetryCount);
        if (failureResult.retryCount() >= maxRetryCount) {
            closeFinalizationFailedRoom(roomCode, failureResult.retryCount());
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
        relayFinalizationFailureSupport.clearContext();
        relayFinalizationAttemptSupport.clearContext();
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
            relayFinalizationAttemptSupport.saveActiveAttempt(attempt);
            RelayRoomEventLogger.apiBusiness("relay_finalization_attempt_started",
                metadata("room_id", roomCode, "attempt_id", attemptId, "retry_count",
                    relayFinalizationFailureSupport.currentFailureCount(roomCode), "max_retry_count", maxRetryCount));
            return processLockedFinalizingRoom(roomCode);
        } finally {
            relayFinalizationAttemptSupport.clearActiveAttempt(roomCode, attemptId);
            relayRoomRepository.releaseFinalizationLock(roomCode, lockToken);
        }
    }

    private String createFinalizationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=finalization,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    /**
     * 최신 Redis 상태를 기준으로 결과물을 만들고 방 상태를 FINISHED로 전환합니다.
     */
    private RelayRoomFinalizationResult processLockedFinalizingRoom(String roomCode) {
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINALIZING) {
            relayFinalizationFailureSupport.clearFailureCount(roomCode);
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long startedNanos = System.nanoTime();
        List<Integer> canvasIndexes = findCanvasIndexes(roomState);
        if (canvasIndexes.isEmpty()) {
            InternalServerException error = new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
            relayFinalizationFailureSupport.recordFailure(roomCode, "process", null, error);
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
            relayFinalizationFailureSupport.recordFailure(roomCode, "redis_update",
                artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), error);
            throw error;
        }
        relayInviteMetadataSyncService.syncWithRoomState(finishedRoomState);

        RelayRoomFinalizationResult result = RelayRoomFinalizationResult.finished(roomCode, artifacts, now);
        relayFinalizationFailureSupport.clearFailureCount(roomCode);
        relayRoomEventPublisher.publishResultCreated(result);
        if (resolvedArtifacts.recovered()) {
            RelayRoomEventLogger.websocketBusiness("relay_finalization_recovered",
                metadata("room_id", roomCode, "attempt_id", relayFinalizationAttemptSupport.currentAttemptId(),
                    "recovery_reason", "existing_result_found", "result_count", result.resultCount()));
        } else {
            RelayRoomEventLogger.websocketBusiness("relay_result_created",
                metadata("room_id", roomCode, "attempt_id", relayFinalizationAttemptSupport.currentAttemptId(),
                    "result_count", result.resultCount(), "artifact_ids",
                    artifacts.stream().map(artifact -> artifact.artifactId().toString()).toList(), "duration_ms",
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()));
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
                    relayFinalizationAttemptSupport::registerAttemptObjectKey,
                    (stage, artifactId, error) -> relayFinalizationFailureSupport.recordFailure(roomState.roomCode(),
                        stage, artifactId, error));
            } catch (RuntimeException e) {
                relayFinalizationCleanupSupport.cleanupCurrentAttemptResultObjects(roomState.roomCode());
                throw e;
            }
            try {
                relayArtifactRepository.saveRelayDrawingResults(roomState.roomCode(), artifacts,
                    findParticipantUuidValues(roomState), now);
                return new ResolvedRelayFinalizationArtifacts(artifacts, false);
            } catch (RuntimeException e) {
                relayFinalizationFailureSupport.recordFailure(roomState.roomCode(), "db_save",
                    artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), e);
                relayFinalizationCleanupSupport.cleanupCurrentAttemptResultObjects(roomState.roomCode());
                throw e;
            }
        }

        if (matchesExpectedCanvasIndexes(existingArtifacts, canvasIndexes)) {
            return new ResolvedRelayFinalizationArtifacts(existingArtifacts, true);
        }

        InternalServerException error = new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
        relayFinalizationFailureSupport.recordFailure(roomState.roomCode(), "process", null, error);
        throw error;
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
}
