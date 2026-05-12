package com.nemonicworld.flipbook.service.disconnect;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationService;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import com.nemonicworld.flipbook.service.timeout.FlipbookFrameAutoSubmissionResult;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 게임 중 재접속 유예가 끝난 플립북 참여자를 이탈 확정하고, 필요하면 방장을 승계합니다.
 */
@Service
public class FlipbookRoomDisconnectGraceService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomDisconnectGraceService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;
    private final FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider;
    private final FlipbookRoomFinalizationService flipbookRoomFinalizationService;
    private final Duration roomMutationLockTtl;
    private final int scanLimit;

    public FlipbookRoomDisconnectGraceService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookSubmissionLockRepository flipbookSubmissionLockRepository,
        FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider,
        FlipbookRoomFinalizationService flipbookRoomFinalizationService,
        @Value("${nemonic.flipbook.disconnect.scan-limit:100}") int scanLimit,
        @Value("${nemonic.flipbook.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookSubmissionLockRepository = flipbookSubmissionLockRepository;
        this.flipbookRoomMutationLockRepository = flipbookRoomMutationLockRepository;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookRuntimeSettingsProvider = flipbookRuntimeSettingsProvider;
        this.flipbookRoomFinalizationService = flipbookRoomFinalizationService;
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
        this.scanLimit = scanLimit;
    }

    /**
     * 처리 후보 PLAYING 방을 스캔하고 각 방을 독립적으로 처리합니다.
     */
    public FlipbookDisconnectGraceProcessResult processDroppedParticipants() {
        return processDroppedParticipants(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 현재 시각을 주입받아 스캔합니다.
     */
    public FlipbookDisconnectGraceProcessResult processDroppedParticipants(LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Duration reconnectGrace = flipbookRuntimeSettingsProvider.currentReconnectGracePeriod();
        LocalDateTime disconnectCutoff = processedAt.minus(reconnectGrace);
        List<FlipbookRoomState> candidateRooms = flipbookRoomRepository
            .findPlayingRoomsForDisconnectGrace(disconnectCutoff, scanLimit);
        int processedRoomCount = 0;
        int droppedParticipantCount = 0;
        int autoSubmittedCount = 0;

        for (FlipbookRoomState candidateRoom : candidateRooms) {
            try {
                FlipbookDisconnectGraceRoomResult result = processRoom(candidateRoom.roomCode(), processedAt,
                    reconnectGrace);
                if (result.processed()) {
                    processedRoomCount++;
                    droppedParticipantCount += result.droppedParticipants().size();
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                log.warn("플립북 방 이탈 확정 처리 중 오류가 발생했습니다. roomCode={}", candidateRoom.roomCode(), e);
                FlipbookRoomEventLogger.apiWarn("flipbook_disconnect_grace_scheduler_failed",
                    "failed to process flipbook disconnect grace room", metadata("room_id", candidateRoom.roomCode()),
                    e);
            }
        }

        return new FlipbookDisconnectGraceProcessResult(candidateRooms.size(), processedRoomCount,
            droppedParticipantCount, autoSubmittedCount);
    }

    /**
     * 방 하나를 최신 Redis 상태 기준으로 재확인한 뒤 CAS로 저장합니다.
     */
    public FlipbookDisconnectGraceRoomResult processRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Duration reconnectGrace = flipbookRuntimeSettingsProvider.currentReconnectGracePeriod();
        return processRoom(roomCode, processedAt, reconnectGrace);
    }

    private FlipbookDisconnectGraceRoomResult processRoom(String roomCode, LocalDateTime processedAt,
        Duration reconnectGrace) {
        FlipbookRoomState candidateRoomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (candidateRoomState == null || candidateRoomState.status() != FlipbookRoomStatus.PLAYING
            || candidateRoomState.currentRound() == null) {
            return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
        }

        String roomMutationLockToken = createRoomMutationLockToken("disconnect-grace", roomCode);
        boolean locked = flipbookRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
            roomMutationLockTtl);
        if (!locked) {
            log.warn("플립북 이탈 확정 처리를 건너뜁니다. 방 상태 변경 잠금이 사용 중입니다. roomCode={}", roomCode);
            FlipbookRoomEventLogger.apiWarn("flipbook_room_mutation_lock_busy",
                "flipbook disconnect grace skipped because room mutation lock is busy",
                metadata("room_id", roomCode, "lock_owner", "disconnect-grace"), null);
            return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
        }

        try {
            FlipbookDisconnectGraceRoomResult result = processRoomWithLock(roomCode, processedAt, reconnectGrace);
            publishDisconnectGraceEvents(result);

            return result;
        } finally {
            flipbookRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private FlipbookDisconnectGraceRoomResult processRoomWithLock(String roomCode, LocalDateTime processedAt,
        Duration reconnectGrace) {
        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (roomState == null || roomState.status() != FlipbookRoomStatus.PLAYING
                || roomState.currentRound() == null) {
                return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
            }

            ParticipantDropUpdate participantDropUpdate = dropExpiredParticipants(roomState, processedAt,
                reconnectGrace);
            AutoSubmitUpdate autoSubmitUpdate = autoSubmitDroppedCurrentAssignments(roomState,
                participantDropUpdate.participants(), processedAt);

            if (!participantDropUpdate.changed() && autoSubmitUpdate.autoSubmissions().isEmpty()) {
                return FlipbookDisconnectGraceRoomResult.noOp(roomCode);
            }

            FlipbookRoomState updatedRoomState = roomState.withParticipantsAssignmentsHostAndStatus(
                participantDropUpdate.participants(), autoSubmitUpdate.assignments(),
                participantDropUpdate.hostUserUuid(), roomState.status(), processedAt);
            FlipbookRoundAdvanceResult advanceResult = null;
            if (!autoSubmitUpdate.autoSubmissions().isEmpty()) {
                advanceResult = flipbookRoomRoundAdvanceService.advanceRoundIfCompleted(updatedRoomState,
                    roomState.currentRound(), processedAt);
                updatedRoomState = advanceResult.roomState();
            }

            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                return new FlipbookDisconnectGraceRoomResult(roomCode, true,
                    participantDropUpdate.droppedParticipants(), participantDropUpdate.hostChange(),
                    autoSubmitUpdate.autoSubmissions(), advanceResult);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private String createRoomMutationLockToken(String owner, String roomCode) {
        return "token=%s,requestedAt=%s,owner=%s,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, roomCode);
    }

    private ParticipantDropUpdate dropExpiredParticipants(FlipbookRoomState roomState, LocalDateTime droppedAt,
        Duration reconnectGrace) {
        List<FlipbookRoomParticipant> participants = new ArrayList<>(roomState.participants().size());
        List<FlipbookDroppedParticipantResult> droppedParticipants = new ArrayList<>();
        boolean changed = false;

        for (FlipbookRoomParticipant participant : roomState.participants()) {
            if (shouldDrop(participant, droppedAt, reconnectGrace)) {
                FlipbookRoomParticipant droppedParticipant = participant.drop(droppedAt);
                participants.add(droppedParticipant);
                droppedParticipants.add(new FlipbookDroppedParticipantResult(roomState.roomCode(),
                    participant.userUuid(), participant.nickname(), participant.disconnectedAt(), droppedAt));
                changed = true;
            } else {
                participants.add(participant);
            }
        }

        HostTransferUpdate hostTransferUpdate = transferHostIfNeeded(roomState, participants, droppedAt);
        changed = changed || hostTransferUpdate.changed();

        return new ParticipantDropUpdate(hostTransferUpdate.participants(), changed, hostTransferUpdate.hostUserUuid(),
            droppedParticipants, hostTransferUpdate.hostChange());
    }

    private boolean shouldDrop(FlipbookRoomParticipant participant, LocalDateTime now, Duration reconnectGrace) {
        return !participant.dropped() && !participant.connected() && participant.disconnectedAt() != null
            && !participant.disconnectedAt().plus(reconnectGrace).isAfter(now);
    }

    private HostTransferUpdate transferHostIfNeeded(FlipbookRoomState roomState,
        List<FlipbookRoomParticipant> participants, LocalDateTime changedAt) {
        Optional<FlipbookRoomParticipant> currentHost = participants.stream()
            .filter(participant -> participant.userUuid().equals(roomState.hostUserUuid()) || participant.host())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (currentHost.isEmpty() || !currentHost.get().dropped()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        Optional<FlipbookRoomParticipant> newHost = participants.stream()
            .filter(participant -> !participant.dropped() && participant.connected())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (newHost.isEmpty()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        FlipbookRoomParticipant newHostParticipant = newHost.get();
        List<FlipbookRoomParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHostParticipant.userUuid())))
            .toList();
        FlipbookHostChangeResult hostChange = new FlipbookHostChangeResult(roomState.roomCode(),
            currentHost.get().userUuid(), newHostParticipant.userUuid(), newHostParticipant.nickname(), changedAt);

        return new HostTransferUpdate(transferredParticipants, newHostParticipant.userUuid(), true, hostChange);
    }

    private AutoSubmitUpdate autoSubmitDroppedCurrentAssignments(FlipbookRoomState roomState,
        List<FlipbookRoomParticipant> participants, LocalDateTime submittedAt) {
        Set<String> droppedUserUuids = droppedUserUuids(participants);

        if (droppedUserUuids.isEmpty()) {
            return new AutoSubmitUpdate(roomState.assignments(), List.of());
        }

        int currentRound = roomState.currentRound();
        List<FlipbookFrameAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() == currentRound && assignment.status() == FlipbookFrameAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid())
                && !isSubmissionLocked(roomState, assignment)) {
                FlipbookFrameAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new FlipbookFrameAutoSubmissionResult(roomState.roomCode(),
                    findNickname(participants, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new AutoSubmitUpdate(assignments, autoSubmissions);
    }

    private Set<String> droppedUserUuids(List<FlipbookRoomParticipant> participants) {
        Set<String> droppedUserUuids = new HashSet<>();
        for (FlipbookRoomParticipant participant : participants) {
            if (participant.dropped()) {
                droppedUserUuids.add(participant.userUuid());
            }
        }

        return droppedUserUuids;
    }

    private boolean isSubmissionLocked(FlipbookRoomState roomState, FlipbookFrameAssignment assignment) {
        return flipbookSubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.flipbookIndex(),
            assignment.frameIndex(), assignment.round(), assignment.assignedUserUuid());
    }

    private FlipbookFrameAssignment autoSubmitAssignment(FlipbookFrameAssignment assignment,
        LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(),
            assignment.assignedUserUuid(), FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, true, true,
            submittedAt);
    }

    private String findNickname(List<FlipbookRoomParticipant> participants, String userUuid) {
        return participants.stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
    }

    private void publishDisconnectGraceEvents(FlipbookDisconnectGraceRoomResult result) {
        for (FlipbookDroppedParticipantResult droppedParticipant : result.droppedParticipants()) {
            flipbookRoomEventPublisher.publishParticipantDropped(droppedParticipant);
            FlipbookRoomEventLogger.websocketBusiness("flipbook_participant_dropped",
                metadata("room_id", droppedParticipant.roomCode(), "uuid", droppedParticipant.userUuid(),
                    "disconnected_at", droppedParticipant.disconnectedAt(), "dropped_at",
                    droppedParticipant.droppedAt()));
        }

        if (result.hostChange() != null) {
            flipbookRoomEventPublisher.publishHostChanged(result.hostChange());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_host_changed",
                metadata("room_id", result.hostChange().roomCode(), "previous_host_uuid",
                    result.hostChange().previousHostUserUuid(), "new_host_uuid",
                    result.hostChange().newHostUserUuid()));
        }

        for (FlipbookFrameAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            flipbookRoomEventPublisher.publishFrameAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_frame_auto_submitted",
                metadata("room_id", autoSubmission.roomCode(), "round", autoSubmission.assignment().round(),
                    "flipbook_index", autoSubmission.assignment().flipbookIndex(), "frame_index",
                    autoSubmission.assignment().frameIndex(), "uuid", autoSubmission.assignment().assignedUserUuid(),
                    "reason", "disconnect_grace"));
        }

        FlipbookRoundAdvanceResult advanceResult = result.advanceResult();
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allRoundsCompleted()) {
            flipbookRoomEventPublisher.publishAllRoundsCompleted(result.roomCode(), advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_all_rounds_completed",
                metadata("room_id", result.roomCode(), "room_status", advanceResult.roomState().status(),
                    "total_rounds", advanceResult.roomState().totalRounds()));
            flipbookRoomFinalizationService.triggerFinalization(result.roomCode());
        } else {
            int previousRound = result.autoSubmissions().get(0).assignment().round();
            flipbookRoomEventPublisher.publishRoundStarted(result.roomCode(), previousRound, advanceResult.nextRound(),
                advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_round_started",
                metadata("room_id", result.roomCode(), "previous_round", previousRound, "round",
                    advanceResult.nextRound(), "round_deadline_at", advanceResult.nextRoundDeadlineAt()));
        }
    }

    private record ParticipantDropUpdate(List<FlipbookRoomParticipant> participants, boolean changed,
        String hostUserUuid, List<FlipbookDroppedParticipantResult> droppedParticipants,
        FlipbookHostChangeResult hostChange) {
    }

    private record HostTransferUpdate(List<FlipbookRoomParticipant> participants, String hostUserUuid, boolean changed,
        FlipbookHostChangeResult hostChange) {
    }

    private record AutoSubmitUpdate(List<FlipbookFrameAssignment> assignments,
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions) {
    }
}
