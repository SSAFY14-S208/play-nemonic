package com.nemonicworld.flipbook.service.finalization;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookArtifactRepository;
import com.nemonicworld.flipbook.repository.FlipbookFinalizationRetryRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseCommand;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * FINALIZING 방의 flipbookIndex별 최종 GIF를 만들고 artifact/gallery 저장 후 방을 FINISHED로
 * 전환합니다.
 */
@Service
public class FlipbookRoomFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomFinalizationService.class);
    private static final String FINALIZATION_STATE_ERROR_MESSAGE = "플립북 최종화 상태가 올바르지 않습니다.";
    private static final String NO_RESULT_FRAMES_CLOSE_REASON = "no_result_frames";

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookArtifactRepository flipbookArtifactRepository;
    private final FlipbookFinalizationArtifactCreator flipbookFinalizationArtifactCreator;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookFinalizationRetryRepository flipbookFinalizationRetryRepository;
    private final FlipbookRoomCloseCommand flipbookRoomCloseCommand;
    private final int scanLimit;
    private final Duration lockTtl;
    private final Duration finalizationReadyDelay;
    private final int maxRetryCount;

    public FlipbookRoomFinalizationService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookArtifactRepository flipbookArtifactRepository,
        FlipbookFinalizationArtifactCreator flipbookFinalizationArtifactCreator,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookFinalizationRetryRepository flipbookFinalizationRetryRepository,
        FlipbookRoomCloseCommand flipbookRoomCloseCommand,
        @Value("${nemonic.flipbook.finalization.scan-limit:50}") int scanLimit,
        @Value("${nemonic.flipbook.finalization.lock-ttl-seconds:60}") long lockTtlSeconds,
        @Value("${nemonic.flipbook.finalization.ready-delay-ms:1000}") long readyDelayMs,
        @Value("${nemonic.flipbook.finalization.max-retry-count:60}") int maxRetryCount) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookArtifactRepository = flipbookArtifactRepository;
        this.flipbookFinalizationArtifactCreator = flipbookFinalizationArtifactCreator;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookFinalizationRetryRepository = flipbookFinalizationRetryRepository;
        this.flipbookRoomCloseCommand = flipbookRoomCloseCommand;
        this.scanLimit = scanLimit;
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        this.finalizationReadyDelay = Duration.ofMillis(Math.max(0L, readyDelayMs));
        this.maxRetryCount = Math.max(1, maxRetryCount);
    }

    /**
     * 스케줄러가 찾은 FINALIZING 방들을 순회하며 최종화를 시도합니다.
     */
    public FlipbookFinalizationProcessResult processFinalizingRooms() {
        List<FlipbookRoomState> finalizingRooms = flipbookRoomRepository.findFinalizingRooms(scanLimit);
        LocalDateTime readyCutoff = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minus(finalizationReadyDelay);
        int processedRoomCount = 0;
        int resultCount = 0;

        for (FlipbookRoomState finalizingRoom : finalizingRooms) {
            if (!isReadyForFinalization(finalizingRoom, readyCutoff)) {
                continue;
            }

            try {
                FlipbookRoomFinalizationResult result = processFinalizingRoom(finalizingRoom.roomCode());
                if (result.processed()) {
                    processedRoomCount++;
                    resultCount += result.resultCount();
                }
            } catch (RuntimeException e) {
                handleFinalizationFailure(finalizingRoom.roomCode(), e);
                log.warn("플립북 최종 GIF 결과물 생성 중 오류가 발생했습니다. roomCode={}", finalizingRoom.roomCode(), e);
            }
        }

        return new FlipbookFinalizationProcessResult(finalizingRooms.size(), processedRoomCount, resultCount);
    }

    public FlipbookRoomFinalizationResult triggerFinalization(String roomCode) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_finalization_immediate_triggered",
            metadata("room_id", roomCode, "trigger_reason", "all_rounds_completed"));
        try {
            return processFinalizingRoom(roomCode);
        } catch (RuntimeException e) {
            handleFinalizationFailure(roomCode, e);
            log.warn("플립북 최종 GIF 결과물 즉시 생성 중 오류가 발생했습니다. roomCode={}", roomCode, e);
            return FlipbookRoomFinalizationResult.noOp(roomCode);
        }
    }

    private void handleFinalizationFailure(String roomCode, RuntimeException error) {
        int retryCount = flipbookFinalizationRetryRepository.incrementFailureCount(roomCode,
            FlipbookRoomRepository.ROOM_STATE_TTL);
        FlipbookRoomEventLogger.apiWarn("flipbook_finalization_failed", "failed to finalize flipbook room",
            metadata("room_id", roomCode, "operation", "finalization", "retry_count", retryCount, "max_retry_count",
                maxRetryCount, "error", error.getClass().getSimpleName()),
            error);

        if (retryCount >= maxRetryCount) {
            closeFinalizationFailedRoom(roomCode, retryCount);
        }
    }

    private void closeFinalizationFailedRoom(String roomCode, int retryCount) {
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINALIZING) {
            return;
        }

        FlipbookRoomCloseResult closeResult = flipbookRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
        if (!closeResult.closed()) {
            return;
        }

        FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", "finalization_failed", "room_status_before",
                roomState.status(), "participant_count", roomState.participantCount(), "retry_count", retryCount,
                "closed_at", closeResult.closedAt()));
        flipbookRoomEventPublisher.publishRoomClosed(roomCode, closeResult.closedAt(), "finalization_failed");
    }

    private boolean isReadyForFinalization(FlipbookRoomState roomState, LocalDateTime readyCutoff) {
        return roomState.updatedAt() == null || !roomState.updatedAt().isAfter(readyCutoff);
    }

    /**
     * 한 방에 대한 최종화 lock을 획득한 뒤 실제 최종화 처리를 실행합니다.
     */
    public FlipbookRoomFinalizationResult processFinalizingRoom(String roomCode) {
        String lockToken = createFinalizationLockToken(roomCode);
        if (!flipbookRoomRepository.acquireFinalizationLock(roomCode, lockToken, lockTtl)) {
            FlipbookRoomEventLogger.apiWarn("flipbook_finalization_lock_busy",
                "flipbook finalization skipped because finalization lock is busy", metadata("room_id", roomCode), null);
            return FlipbookRoomFinalizationResult.noOp(roomCode);
        }

        try {
            return processLockedFinalizingRoom(roomCode);
        } finally {
            flipbookRoomRepository.releaseFinalizationLock(roomCode, lockToken);
        }
    }

    private String createFinalizationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=finalization,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    /**
     * 최신 Redis 상태를 기준으로 GIF 결과물을 만들고 방 상태를 FINISHED로 전환합니다.
     */
    private FlipbookRoomFinalizationResult processLockedFinalizingRoom(String roomCode) {
        FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINALIZING) {
            flipbookFinalizationRetryRepository.clearFailureCount(roomCode);
            return FlipbookRoomFinalizationResult.noOp(roomCode);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Integer> expectedIndexes = flipbookFinalizationArtifactCreator.findResultFlipbookIndexes(roomState);
        if (expectedIndexes.isEmpty()) {
            return closeNoResultFramesRoom(roomState, now);
        }

        List<FlipbookResultArtifactResult> existingArtifacts = flipbookArtifactRepository
            .findFlipbookArtifactsBySourceRoomId(roomCode);
        List<FlipbookResultArtifactResult> artifacts = resolveArtifacts(roomState, expectedIndexes, existingArtifacts,
            now);
        FlipbookRoomState finishedRoomState = roomState.finish(now);
        if (!flipbookRoomRepository.saveIfUnchanged(roomState, finishedRoomState)) {
            throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        }
        flipbookInviteMetadataSyncService.syncWithRoomState(finishedRoomState);

        FlipbookRoomFinalizationResult result = FlipbookRoomFinalizationResult.finished(roomCode, artifacts, now);
        flipbookFinalizationRetryRepository.clearFailureCount(roomCode);
        flipbookRoomEventPublisher.publishResultCreated(result);
        FlipbookRoomEventLogger.websocketBusiness("flipbook_result_created",
            metadata("room_id", roomCode, "room_status", result.roomStatus(), "result_count", result.resultCount(),
                "artifact_ids", artifacts.stream().map(artifact -> artifact.artifactId().toString()).toList()));

        return result;
    }

    private FlipbookRoomFinalizationResult closeNoResultFramesRoom(FlipbookRoomState roomState,
        LocalDateTime closedAt) {
        FlipbookRoomCloseResult closeResult = flipbookRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
        if (!closeResult.closed()) {
            throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        }

        flipbookFinalizationRetryRepository.clearFailureCount(roomState.roomCode());
        flipbookRoomEventPublisher.publishRoomClosed(roomState.roomCode(), closeResult.closedAt(),
            NO_RESULT_FRAMES_CLOSE_REASON);
        FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
            metadata("room_id", roomState.roomCode(), "close_reason", NO_RESULT_FRAMES_CLOSE_REASON,
                "room_status_before", roomState.status(), "participant_count", roomState.participantCount(),
                "total_rounds", roomState.totalRounds(), "closed_at", closeResult.closedAt()));

        return FlipbookRoomFinalizationResult.closed(roomState.roomCode(), closeResult.closedAt());
    }

    /**
     * 이미 생성된 결과물이 있으면 재사용하고, 없으면 새로 합성해 DB에 저장합니다.
     */
    private List<FlipbookResultArtifactResult> resolveArtifacts(FlipbookRoomState roomState,
        List<Integer> expectedIndexes, List<FlipbookResultArtifactResult> existingArtifacts, LocalDateTime now) {
        if (existingArtifacts.isEmpty()) {
            List<FlipbookResultArtifactResult> artifacts = flipbookFinalizationArtifactCreator
                .createAndUploadResults(roomState);
            flipbookArtifactRepository.saveFlipbookResults(roomState.roomCode(), artifacts,
                findParticipantUuidValues(roomState), now);

            return artifacts;
        }

        if (matchesExpectedFlipbookIndexes(existingArtifacts, expectedIndexes)) {
            return existingArtifacts;
        }

        throw new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
    }

    private List<String> findParticipantUuidValues(FlipbookRoomState roomState) {
        return roomState.participants().stream().filter(participant -> !participant.dropped())
            .map(FlipbookRoomParticipant::userUuid).distinct().toList();
    }

    private boolean matchesExpectedFlipbookIndexes(List<FlipbookResultArtifactResult> existingArtifacts,
        List<Integer> expectedIndexes) {
        List<Integer> existingIndexes = existingArtifacts.stream().map(FlipbookResultArtifactResult::flipbookIndex)
            .distinct().sorted().toList();

        return existingArtifacts.size() == expectedIndexes.size() && existingIndexes.equals(expectedIndexes);
    }

}
