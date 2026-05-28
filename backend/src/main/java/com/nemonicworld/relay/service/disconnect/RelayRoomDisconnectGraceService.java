package com.nemonicworld.relay.service.disconnect;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayPartTransitionUseCase;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;
import com.nemonicworld.relay.service.timeout.RelayRoomAutoSubmitUpdate;
import com.nemonicworld.relay.service.timeout.RelayRoomAutoSubmitUseCase;
import com.nemonicworld.relay.service.timeout.RelayRoomAutoSubmissionResult;
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
 * 게임 중 재접속 유예가 끝난 참여자를 이탈 확정하고, 해당 참여자의 현재 파트 배정을 흰 캔버스로 자동 제출합니다.
 */
@Service
public class RelayRoomDisconnectGraceService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomDisconnectGraceService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomMutationLockRepository relayRoomMutationLockRepository;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelayRoomAutoSubmitUseCase relayRoomAutoSubmitUseCase;
    private final RelayPartTransitionUseCase relayPartTransitionUseCase;
    private final RelayDisconnectGraceParticipantUseCase relayDisconnectGraceParticipantUseCase;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;
    private final Duration roomMutationLockTtl;
    private final int scanLimit;

    public RelayRoomDisconnectGraceService(RelayRoomRepository relayRoomRepository,
        RelayRoomMutationLockRepository relayRoomMutationLockRepository,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService, RelayRoomAutoSubmitUseCase relayRoomAutoSubmitUseCase,
        RelayPartTransitionUseCase relayPartTransitionUseCase,
        RelayDisconnectGraceParticipantUseCase relayDisconnectGraceParticipantUseCase,
        RelayRoomEventPublisher relayRoomEventPublisher, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRuntimeSettingsProvider relayRuntimeSettingsProvider,
        @Value("${nemonic.relay.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs,
        @Value("${nemonic.relay.disconnect.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomMutationLockRepository = relayRoomMutationLockRepository;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relayRoomAutoSubmitUseCase = relayRoomAutoSubmitUseCase;
        this.relayPartTransitionUseCase = relayPartTransitionUseCase;
        this.relayDisconnectGraceParticipantUseCase = relayDisconnectGraceParticipantUseCase;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
        this.scanLimit = scanLimit;
    }

    /**
     * 처리 후보 PLAYING 방을 스캔하고 각 방을 독립적으로 처리합니다.
     */
    public RelayDisconnectGraceProcessResult processDroppedParticipants() {
        return processDroppedParticipants(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 현재 시각을 주입받아 스캔합니다.
     */
    public RelayDisconnectGraceProcessResult processDroppedParticipants(LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Duration reconnectGrace = relayRuntimeSettingsProvider.currentReconnectGracePeriod();
        LocalDateTime disconnectCutoff = processedAt.minus(reconnectGrace);
        List<RelayRoomState> candidateRooms = relayRoomRepository.findPlayingRoomsForDisconnectGrace(disconnectCutoff,
            scanLimit);
        int processedRoomCount = 0;
        int droppedParticipantCount = 0;
        int autoSubmittedCount = 0;

        for (RelayRoomState candidateRoom : candidateRooms) {
            try {
                RelayDisconnectGraceRoomResult result = processRoom(candidateRoom.roomCode(), processedAt,
                    reconnectGrace);
                if (result.processed()) {
                    processedRoomCount++;
                    droppedParticipantCount += result.droppedParticipants().size();
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                RelayRoomEventLogger.apiWarn("relay_disconnect_grace_scheduler_failed",
                    "failed to process relay disconnect grace room",
                    metadata(
                        "room_id", candidateRoom.roomCode(), "uuid", relayDisconnectGraceParticipantUseCase
                            .findDisconnectGraceCandidateUuid(candidateRoom, processedAt, reconnectGrace),
                        "operation", "disconnect_grace"),
                    e);
                log.warn("릴레이 방 이탈 확정 처리 중 오류가 발생했습니다. roomCode={}", candidateRoom.roomCode(), e);
            }
        }

        return new RelayDisconnectGraceProcessResult(candidateRooms.size(), processedRoomCount, droppedParticipantCount,
            autoSubmittedCount);
    }

    /**
     * 방 하나를 최신 Redis 상태 기준으로 재확인한 뒤 CAS로 저장합니다.
     */
    public RelayDisconnectGraceRoomResult processRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Duration reconnectGrace = relayRuntimeSettingsProvider.currentReconnectGracePeriod();
        return processRoom(roomCode, processedAt, reconnectGrace);
    }

    private RelayDisconnectGraceRoomResult processRoom(String roomCode, LocalDateTime processedAt,
        Duration reconnectGrace) {
        RelayRoomState candidateRoomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (!needsDisconnectGraceProcessing(candidateRoomState, processedAt, reconnectGrace)) {
            return RelayDisconnectGraceRoomResult.noOp(roomCode);
        }

        String roomMutationLockToken = createRoomMutationLockToken(roomCode);
        boolean locked = relayRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
            roomMutationLockTtl);
        if (!locked) {
            RelayRoomEventLogger.apiWarn("relay_room_mutation_lock_busy",
                "relay disconnect grace skipped because room mutation lock was busy", metadata("room_id", roomCode,
                    "operation", "disconnect_grace", "lock_ttl_ms", roomMutationLockTtl.toMillis()),
                null);
            return RelayDisconnectGraceRoomResult.noOp(roomCode);
        }

        try {
            return processRoomWithLock(roomCode, processedAt, reconnectGrace);
        } finally {
            relayRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private RelayDisconnectGraceRoomResult processRoomWithLock(String roomCode, LocalDateTime processedAt,
        Duration reconnectGrace) {
        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (roomState == null || roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null) {
                return RelayDisconnectGraceRoomResult.noOp(roomCode);
            }

            RelayParticipantDropUpdate participantDropUpdate = relayDisconnectGraceParticipantUseCase
                .dropExpiredParticipants(roomState, processedAt, reconnectGrace);
            RelayRoomAutoSubmitUpdate autoSubmitUpdate = relayRoomAutoSubmitUseCase
                .autoSubmitDroppedCurrentAssignments(roomState, participantDropUpdate.participants(), processedAt);

            if (!participantDropUpdate.changed() && autoSubmitUpdate.autoSubmissions().isEmpty()) {
                return RelayDisconnectGraceRoomResult.noOp(roomCode);
            }

            RelayRoomState updatedRoomState = roomState.withParticipantsAssignmentsAndHost(
                participantDropUpdate.participants(), autoSubmitUpdate.assignments(),
                participantDropUpdate.hostUserUuid(), processedAt);
            RelayPartAdvanceResult advanceResult = null;
            if (!autoSubmitUpdate.autoSubmissions().isEmpty()) {
                advanceResult = relayRoomPartAdvanceService.advancePartIfCompleted(updatedRoomState,
                    roomState.currentPart(), processedAt);
                updatedRoomState = advanceResult.roomState();
            }

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                RelayDisconnectGraceRoomResult result = new RelayDisconnectGraceRoomResult(roomCode, true,
                    participantDropUpdate.droppedParticipants(), participantDropUpdate.hostChange(),
                    autoSubmitUpdate.autoSubmissions(), advanceResult);
                publishDisconnectGraceEvents(result);

                return result;
            }
        }

        RelayRoomEventLogger.apiWarn("relay_redis_cas_retry_exceeded",
            "relay disconnect grace exceeded Redis CAS retry count", metadata("room_id", roomCode, "operation",
                "disconnect_grace", "attempt_count", RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES),
            null);
        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private boolean needsDisconnectGraceProcessing(RelayRoomState roomState, LocalDateTime now,
        Duration reconnectGrace) {
        if (roomState == null || roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null) {
            return false;
        }

        return relayDisconnectGraceParticipantUseCase.needsDisconnectGraceProcessing(roomState, now, reconnectGrace);
    }

    private String createRoomMutationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=disconnect-grace,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    private void publishDisconnectGraceEvents(RelayDisconnectGraceRoomResult result) {
        for (RelayDroppedParticipantResult droppedParticipant : result.droppedParticipants()) {
            relayRoomEventPublisher.publishParticipantDropped(droppedParticipant);
            RelayRoomEventLogger.websocketBusiness("relay_participant_dropped",
                metadata("room_id", droppedParticipant.roomCode(), "uuid", droppedParticipant.userUuid(),
                    "disconnected_at", droppedParticipant.disconnectedAt(), "dropped_at",
                    droppedParticipant.droppedAt(), "current_part",
                    result.autoSubmissions().stream().findFirst()
                        .map(autoSubmission -> autoSubmission.assignment().part()).orElse(null),
                    "auto_submitted_count", countAutoSubmissions(result, droppedParticipant.userUuid())));
        }

        if (result.hostChange() != null) {
            relayRoomEventPublisher.publishHostChanged(result.hostChange());
            RelayRoomEventLogger.websocketBusiness("relay_host_changed",
                metadata("room_id", result.hostChange().roomCode(), "previous_host_uuid",
                    result.hostChange().previousHostUserUuid(), "new_host_uuid", result.hostChange().newHostUserUuid(),
                    "reason", "host_dropped"));
        }

        for (RelayRoomAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            relayRoomEventPublisher.publishPartAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
            RelayRoomEventLogger.websocketBusiness("relay_part_auto_submitted",
                metadata("room_id", autoSubmission.roomCode(), "uuid", autoSubmission.assignment().assignedUserUuid(),
                    "canvas_index", autoSubmission.assignment().canvasIndex(), "part",
                    autoSubmission.assignment().part(), "reason", "participant_dropped", "empty",
                    autoSubmission.assignment().empty()));
        }

        RelayDrawingPart previousPart = result.autoSubmissions().stream().findFirst()
            .map(autoSubmission -> autoSubmission.assignment().part()).orElse(null);
        relayPartTransitionUseCase.publishTransitionEvents(result.roomCode(), previousPart, result.advanceResult());
    }

    private long countAutoSubmissions(RelayDisconnectGraceRoomResult result, String userUuid) {
        return result.autoSubmissions().stream()
            .filter(autoSubmission -> autoSubmission.assignment().assignedUserUuid().equals(userUuid)).count();
    }

}
