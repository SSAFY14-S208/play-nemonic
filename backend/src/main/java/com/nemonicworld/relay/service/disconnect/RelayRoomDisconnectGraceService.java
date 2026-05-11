package com.nemonicworld.relay.service.disconnect;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.timeout.RelayRoomAutoSubmissionResult;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
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
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 게임 중 재접속 유예가 끝난 참여자를 이탈 확정하고, 해당 참여자의 현재 파트 배정을 흰 캔버스로 자동 제출합니다.
 */
@Service
public class RelayRoomDisconnectGraceService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomDisconnectGraceService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelaySubmissionLockRepository relaySubmissionLockRepository;
    private final RelayRoomMutationLockRepository relayRoomMutationLockRepository;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final Duration reconnectGrace;
    private final Duration roomMutationLockTtl;
    private final int scanLimit;

    public RelayRoomDisconnectGraceService(RelayRoomRepository relayRoomRepository,
        RelaySubmissionLockRepository relaySubmissionLockRepository,
        RelayRoomMutationLockRepository relayRoomMutationLockRepository,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService, RelayRoomEventPublisher relayRoomEventPublisher,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        @Value("${nemonic.relay.disconnect.reconnect-grace-seconds:" + RelayRoomPolicy.DEFAULT_RECONNECT_GRACE_SECONDS
            + "}") long reconnectGraceSeconds,
        @Value("${nemonic.relay.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs,
        @Value("${nemonic.relay.disconnect.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relaySubmissionLockRepository = relaySubmissionLockRepository;
        this.relayRoomMutationLockRepository = relayRoomMutationLockRepository;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.reconnectGrace = Duration.ofSeconds(Math.max(0L, reconnectGraceSeconds));
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
        LocalDateTime disconnectCutoff = processedAt.minus(reconnectGrace);
        List<RelayRoomState> candidateRooms = relayRoomRepository.findPlayingRoomsForDisconnectGrace(disconnectCutoff,
            scanLimit);
        int processedRoomCount = 0;
        int droppedParticipantCount = 0;
        int autoSubmittedCount = 0;

        for (RelayRoomState candidateRoom : candidateRooms) {
            try {
                RelayDisconnectGraceRoomResult result = processRoom(candidateRoom.roomCode(), processedAt);
                if (result.processed()) {
                    processedRoomCount++;
                    droppedParticipantCount += result.droppedParticipants().size();
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                RelayRoomEventLogger.apiWarn("relay_disconnect_grace_scheduler_failed",
                    "failed to process relay disconnect grace room",
                    metadata("room_id", candidateRoom.roomCode(), "uuid",
                        findDisconnectGraceCandidateUuid(candidateRoom, processedAt), "operation", "disconnect_grace"),
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
        RelayRoomState candidateRoomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (!needsDisconnectGraceProcessing(candidateRoomState, processedAt)) {
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
            return processRoomWithLock(roomCode, processedAt);
        } finally {
            relayRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private RelayDisconnectGraceRoomResult processRoomWithLock(String roomCode, LocalDateTime processedAt) {
        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (roomState == null || roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null) {
                return RelayDisconnectGraceRoomResult.noOp(roomCode);
            }

            ParticipantDropUpdate participantDropUpdate = dropExpiredParticipants(roomState, processedAt);
            AutoSubmitUpdate autoSubmitUpdate = autoSubmitDroppedCurrentAssignments(roomState,
                participantDropUpdate.participants(), processedAt);

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

    private boolean needsDisconnectGraceProcessing(RelayRoomState roomState, LocalDateTime now) {
        if (roomState == null || roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null) {
            return false;
        }

        return hasExpiredDisconnectedParticipant(roomState, now)
            || hasDroppedParticipantPendingCurrentAssignment(roomState)
            || hasDroppedHostWithConnectedCandidate(roomState);
    }

    private boolean hasExpiredDisconnectedParticipant(RelayRoomState roomState, LocalDateTime now) {
        return roomState.participants().stream().anyMatch(participant -> shouldDrop(participant, now));
    }

    private boolean hasDroppedParticipantPendingCurrentAssignment(RelayRoomState roomState) {
        Set<String> droppedUserUuids = droppedUserUuids(roomState.participants());
        if (droppedUserUuids.isEmpty()) {
            return false;
        }

        return roomState.assignments().stream()
            .anyMatch(assignment -> assignment.part() == roomState.currentPart()
                && assignment.status() == RelayAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid()));
    }

    private boolean hasDroppedHostWithConnectedCandidate(RelayRoomState roomState) {
        boolean droppedHostExists = roomState.participants().stream().anyMatch(participant -> participant.dropped()
            && (participant.host() || participant.userUuid().equals(roomState.hostUserUuid())));
        if (!droppedHostExists) {
            return false;
        }

        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && participant.connected());
    }

    private String createRoomMutationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=disconnect-grace,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    private ParticipantDropUpdate dropExpiredParticipants(RelayRoomState roomState, LocalDateTime droppedAt) {
        List<RelayRoomParticipant> participants = new ArrayList<>(roomState.participants().size());
        List<RelayDroppedParticipantResult> droppedParticipants = new ArrayList<>();
        boolean changed = false;

        for (RelayRoomParticipant participant : roomState.participants()) {
            if (shouldDrop(participant, droppedAt)) {
                RelayRoomParticipant droppedParticipant = participant.drop(droppedAt);
                participants.add(droppedParticipant);
                droppedParticipants.add(new RelayDroppedParticipantResult(roomState.roomCode(), participant.userUuid(),
                    participant.nickname(), participant.disconnectedAt(), droppedAt));
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

    private boolean shouldDrop(RelayRoomParticipant participant, LocalDateTime now) {
        return !participant.dropped() && !participant.connected() && participant.disconnectedAt() != null
            && !participant.disconnectedAt().plus(reconnectGrace).isAfter(now);
    }

    private String findDisconnectGraceCandidateUuid(RelayRoomState roomState, LocalDateTime now) {
        return roomState.participants().stream()
            .filter(participant -> shouldDrop(participant, now) || participant.dropped())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder)).map(RelayRoomParticipant::userUuid)
            .orElse(null);
    }

    private HostTransferUpdate transferHostIfNeeded(RelayRoomState roomState, List<RelayRoomParticipant> participants,
        LocalDateTime changedAt) {
        Optional<RelayRoomParticipant> currentHost = participants.stream()
            .filter(participant -> participant.userUuid().equals(roomState.hostUserUuid()) || participant.host())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder));

        if (currentHost.isEmpty() || !currentHost.get().dropped()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        Optional<RelayRoomParticipant> newHost = participants.stream()
            .filter(participant -> !participant.dropped() && participant.connected())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder));

        if (newHost.isEmpty()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        RelayRoomParticipant newHostParticipant = newHost.get();
        List<RelayRoomParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHostParticipant.userUuid())))
            .toList();
        RelayHostChangeResult hostChange = new RelayHostChangeResult(roomState.roomCode(), currentHost.get().userUuid(),
            newHostParticipant.userUuid(), newHostParticipant.nickname(), changedAt);

        return new HostTransferUpdate(transferredParticipants, newHostParticipant.userUuid(), true, hostChange);
    }

    private AutoSubmitUpdate autoSubmitDroppedCurrentAssignments(RelayRoomState roomState,
        List<RelayRoomParticipant> participants, LocalDateTime submittedAt) {
        Set<String> droppedUserUuids = droppedUserUuids(participants);

        if (droppedUserUuids.isEmpty()) {
            return new AutoSubmitUpdate(roomState.assignments(), List.of());
        }

        RelayDrawingPart currentPart = roomState.currentPart();
        List<RelayRoomAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<RelayRoomAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() == currentPart && assignment.status() == RelayAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid())
                && !isSubmissionLocked(roomState, assignment)) {
                RelayRoomAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new RelayRoomAutoSubmissionResult(roomState.roomCode(),
                    findNickname(participants, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new AutoSubmitUpdate(assignments, autoSubmissions);
    }

    private Set<String> droppedUserUuids(List<RelayRoomParticipant> participants) {
        Set<String> droppedUserUuids = new HashSet<>();
        for (RelayRoomParticipant participant : participants) {
            if (participant.dropped()) {
                droppedUserUuids.add(participant.userUuid());
            }
        }

        return droppedUserUuids;
    }

    private boolean isSubmissionLocked(RelayRoomState roomState, RelayRoomAssignment assignment) {
        return relaySubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.canvasIndex(),
            assignment.part(), assignment.assignedUserUuid());
    }

    private RelayRoomAssignment autoSubmitAssignment(RelayRoomAssignment assignment, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(assignment.canvasIndex(), assignment.part(), assignment.assignedUserUuid(),
            RelayAssignmentStatus.AUTO_SUBMITTED, assignment.fileId(), null, null, true, true, submittedAt);
    }

    private String findNickname(List<RelayRoomParticipant> participants, String userUuid) {
        return participants.stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(RelayRoomParticipant::nickname).findFirst().orElse(null);
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

        RelayPartAdvanceResult advanceResult = result.advanceResult();
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allPartsCompleted()) {
            relayRoomEventPublisher.publishAllPartsCompleted(result.roomCode(), advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
            RelayRoomEventLogger.websocketBusiness("relay_all_parts_completed", metadata("room_id", result.roomCode(),
                "participant_count", advanceResult.roomState().participantCount(), "assignment_count",
                advanceResult.roomState().assignments().size(), "completed_at", advanceResult.roomState().updatedAt()));
        } else {
            RelayDrawingPart previousPart = result.autoSubmissions().get(0).assignment().part();
            relayRoomEventPublisher.publishPartStarted(result.roomCode(), previousPart, advanceResult.nextPart(),
                advanceResult.nextPartStartedAt(), advanceResult.nextPartDeadlineAt());
            RelayRoomEventLogger.websocketBusiness("relay_part_started",
                metadata("room_id", result.roomCode(), "part", advanceResult.nextPart(), "previous_part", previousPart,
                    "participant_count", advanceResult.roomState().participantCount(), "part_deadline_at",
                    advanceResult.nextPartDeadlineAt()));
        }
    }

    private long countAutoSubmissions(RelayDisconnectGraceRoomResult result, String userUuid) {
        return result.autoSubmissions().stream()
            .filter(autoSubmission -> autoSubmission.assignment().assignedUserUuid().equals(userUuid)).count();
    }

    private record ParticipantDropUpdate(List<RelayRoomParticipant> participants, boolean changed, String hostUserUuid,
        List<RelayDroppedParticipantResult> droppedParticipants, RelayHostChangeResult hostChange) {
    }

    private record HostTransferUpdate(List<RelayRoomParticipant> participants, String hostUserUuid, boolean changed,
        RelayHostChangeResult hostChange) {
    }

    private record AutoSubmitUpdate(List<RelayRoomAssignment> assignments,
        List<RelayRoomAutoSubmissionResult> autoSubmissions) {
    }
}
