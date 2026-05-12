package com.nemonicworld.relay.service.timeout;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartTimeUpEventResponse.PendingSubmission;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.repository.RelayRoomTimeUpNotificationRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 마감 시간이 지난 릴레이 현재 파트의 미제출 배정을 빈 그림으로 자동 제출합니다.
 */
@Service
public class RelayRoomTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomTimeoutService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelaySubmissionLockRepository relaySubmissionLockRepository;
    private final RelayRoomTimeUpNotificationRepository relayRoomTimeUpNotificationRepository;
    private final RelayRoomMutationLockRepository relayRoomMutationLockRepository;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRoomFinalizationService relayRoomFinalizationService;
    private final int scanLimit;
    private final Duration autoSubmitGrace;
    private final Duration roomMutationLockTtl;

    public RelayRoomTimeoutService(RelayRoomRepository relayRoomRepository,
        RelaySubmissionLockRepository relaySubmissionLockRepository,
        RelayRoomTimeUpNotificationRepository relayRoomTimeUpNotificationRepository,
        RelayRoomMutationLockRepository relayRoomMutationLockRepository,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService, RelayRoomEventPublisher relayRoomEventPublisher,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRoomFinalizationService relayRoomFinalizationService,
        @Value("${nemonic.relay.timeout.scan-limit:100}") int scanLimit,
        @Value("${nemonic.relay.timeout.auto-submit-grace-ms:2000}") long autoSubmitGraceMs,
        @Value("${nemonic.relay.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.relayRoomRepository = relayRoomRepository;
        this.relaySubmissionLockRepository = relaySubmissionLockRepository;
        this.relayRoomTimeUpNotificationRepository = relayRoomTimeUpNotificationRepository;
        this.relayRoomMutationLockRepository = relayRoomMutationLockRepository;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRoomFinalizationService = relayRoomFinalizationService;
        this.scanLimit = scanLimit;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    /**
     * Redis에서 마감 시간이 지난 PLAYING 방을 찾아 자동 제출을 처리합니다.
     */
    public RelayTimeoutProcessResult processExpiredRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        publishPartTimeUpEvents(relayRoomRepository.findExpiredPlayingRooms(now, scanLimit), now);
        LocalDateTime autoSubmitCutoff = now.minus(autoSubmitGrace);
        List<RelayRoomState> expiredRooms = relayRoomRepository.findExpiredPlayingRooms(autoSubmitCutoff, scanLimit);
        int processedRoomCount = 0;
        int autoSubmittedCount = 0;

        for (RelayRoomState expiredRoom : expiredRooms) {
            try {
                RelayRoomTimeoutResult result = processExpiredRoom(expiredRoom.roomCode(), now);
                if (result.processed()) {
                    processedRoomCount++;
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                RelayRoomEventLogger.apiWarn("relay_timeout_scheduler_failed", "failed to process relay timeout room",
                    metadata("room_id", expiredRoom.roomCode(), "part", expiredRoom.currentPart(), "operation",
                        "timeout"),
                    e);
                log.warn("릴레이 타임아웃 자동 제출 처리 중 오류가 발생했습니다. roomCode={}", expiredRoom.roomCode(), e);
            }
        }

        return new RelayTimeoutProcessResult(expiredRooms.size(), processedRoomCount, autoSubmittedCount);
    }

    private void publishPartTimeUpEvents(List<RelayRoomState> timeUpRooms, LocalDateTime now) {
        for (RelayRoomState roomState : timeUpRooms) {
            if (!shouldPublishPartTimeUpEvent(roomState, now)) {
                continue;
            }

            try {
                boolean marked = relayRoomTimeUpNotificationRepository.markPartTimeUpNotified(roomState.roomCode(),
                    roomState.currentPart(), roomState.partDeadlineAt(), RelayRoomRepository.ROOM_STATE_TTL);
                if (marked) {
                    LocalDateTime submitGraceDeadlineAt = roomState.partDeadlineAt().plus(autoSubmitGrace);
                    List<PendingSubmission> pendingSubmissions = pendingCurrentSubmissions(roomState);
                    relayRoomEventPublisher.publishPartTimeUp(roomState.roomCode(), roomState.currentPart(),
                        roomState.partDeadlineAt(), submitGraceDeadlineAt, autoSubmitGrace.toMillis(),
                        pendingSubmissions);
                    RelayRoomEventLogger.websocketBusiness("relay_part_time_up",
                        metadata("room_id", roomState.roomCode(), "part", roomState.currentPart(), "pending_count",
                            pendingSubmissions.size(), "pending_user_uuids",
                            pendingSubmissions.stream().map(PendingSubmission::userUuid).toList(), "part_deadline_at",
                            roomState.partDeadlineAt(), "submit_grace_deadline_at", submitGraceDeadlineAt));
                }
            } catch (RuntimeException e) {
                RelayRoomEventLogger.websocketWarn("relay_part_time_up_publish_failed",
                    "failed to publish relay part time-up event",
                    metadata("room_id", roomState.roomCode(), "part", roomState.currentPart()), e);
                log.warn("릴레이 파트 제한 시간 종료 이벤트 발행 중 오류가 발생했습니다. roomCode={}, part={}", roomState.roomCode(),
                    roomState.currentPart(), e);
            }
        }
    }

    /**
     * 지정한 방의 현재 파트가 만료되었으면 PENDING 배정을 AUTO_SUBMITTED로 바꿉니다.
     */
    public RelayRoomTimeoutResult processExpiredRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        RelayRoomState candidateRoomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (!isExpiredPlayingRoom(candidateRoomState, processedAt)) {
            return RelayRoomTimeoutResult.noOp(roomCode);
        }

        String roomMutationLockToken = createRoomMutationLockToken("timeout", roomCode);
        boolean locked = relayRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
            roomMutationLockTtl);
        if (!locked) {
            RelayRoomEventLogger.apiWarn("relay_room_mutation_lock_busy",
                "relay timeout skipped because room mutation lock was busy",
                metadata("room_id", roomCode, "operation", "timeout", "lock_ttl_ms", roomMutationLockTtl.toMillis()),
                null);
            return RelayRoomTimeoutResult.noOp(roomCode);
        }

        try {
            RelayRoomTimeoutResult result = processExpiredRoomWithLock(roomCode, processedAt);
            publishTimeoutEvents(result);

            return result;
        } finally {
            relayRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private RelayRoomTimeoutResult processExpiredRoomWithLock(String roomCode, LocalDateTime processedAt) {
        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (!isExpiredPlayingRoom(roomState, processedAt)) {
                return RelayRoomTimeoutResult.noOp(roomCode);
            }

            RelayDrawingPart currentPart = roomState.currentPart();
            AutoSubmitUpdate autoSubmitUpdate = autoSubmitPendingAssignments(roomState, currentPart, processedAt);
            RelayRoomState submittedRoomState = autoSubmitUpdate.autoSubmissions().isEmpty()
                ? roomState
                : roomState.withAssignments(autoSubmitUpdate.updatedAssignments(), processedAt);
            RelayPartAdvanceResult advanceResult = relayRoomPartAdvanceService
                .advancePartIfCompleted(submittedRoomState, currentPart, processedAt);

            if (autoSubmitUpdate.autoSubmissions().isEmpty() && !advanceResult.advanced()) {
                return RelayRoomTimeoutResult.noOp(roomCode);
            }

            if (relayRoomRepository.saveIfUnchanged(roomState, advanceResult.roomState())) {
                relayInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                return new RelayRoomTimeoutResult(roomCode, true, currentPart, autoSubmitUpdate.autoSubmissions(),
                    advanceResult);
            }
        }

        RelayRoomEventLogger.apiWarn("relay_redis_cas_retry_exceeded", "relay timeout exceeded Redis CAS retry count",
            metadata("room_id", roomCode, "operation", "timeout", "attempt_count",
                RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES),
            null);
        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private String createRoomMutationLockToken(String owner, String roomCode) {
        return "token=%s,requestedAt=%s,owner=%s,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, roomCode);
    }

    private boolean isExpiredPlayingRoom(RelayRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == RelayRoomStatus.PLAYING && roomState.currentPart() != null
            && roomState.partDeadlineAt() != null && !roomState.partDeadlineAt().plus(autoSubmitGrace).isAfter(now);
    }

    private boolean shouldPublishPartTimeUpEvent(RelayRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == RelayRoomStatus.PLAYING && roomState.currentPart() != null
            && roomState.partDeadlineAt() != null && !roomState.partDeadlineAt().isAfter(now)
            && roomState.partDeadlineAt().plus(autoSubmitGrace).isAfter(now) && hasPendingCurrentAssignment(roomState);
    }

    private boolean hasPendingCurrentAssignment(RelayRoomState roomState) {
        return roomState.assignments().stream().anyMatch(assignment -> assignment.part() == roomState.currentPart()
            && assignment.status() == RelayAssignmentStatus.PENDING);
    }

    private List<PendingSubmission> pendingCurrentSubmissions(RelayRoomState roomState) {
        return roomState.assignments().stream()
            .filter(assignment -> assignment.part() == roomState.currentPart()
                && assignment.status() == RelayAssignmentStatus.PENDING)
            .sorted(Comparator.comparingInt(RelayRoomAssignment::canvasIndex)).map(assignment -> {
                RelayRoomParticipant participant = findParticipant(roomState, assignment.assignedUserUuid());
                return new PendingSubmission(assignment.canvasIndex(), assignment.assignedUserUuid(),
                    participant == null ? null : participant.nickname(),
                    participant != null && participant.connected());
            }).toList();
    }

    private RelayRoomParticipant findParticipant(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst().orElse(null);
    }

    private AutoSubmitUpdate autoSubmitPendingAssignments(RelayRoomState roomState, RelayDrawingPart currentPart,
        LocalDateTime submittedAt) {
        List<RelayRoomAssignment> updatedAssignments = new ArrayList<>(roomState.assignments().size());
        List<RelayRoomAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() == currentPart && assignment.status() == RelayAssignmentStatus.PENDING
                && !isSubmissionLocked(roomState, assignment)) {
                RelayRoomAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                updatedAssignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new RelayRoomAutoSubmissionResult(roomState.roomCode(),
                    findNickname(roomState, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return new AutoSubmitUpdate(updatedAssignments, autoSubmissions);
    }

    private RelayRoomAssignment autoSubmitAssignment(RelayRoomAssignment assignment, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(assignment.canvasIndex(), assignment.part(), assignment.assignedUserUuid(),
            RelayAssignmentStatus.AUTO_SUBMITTED, assignment.fileId(), null, null, true, true, submittedAt);
    }

    private boolean isSubmissionLocked(RelayRoomState roomState, RelayRoomAssignment assignment) {
        return relaySubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.canvasIndex(),
            assignment.part(), assignment.assignedUserUuid());
    }

    private String findNickname(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(RelayRoomParticipant::nickname).findFirst().orElse(null);
    }

    private void publishTimeoutEvents(RelayRoomTimeoutResult result) {
        for (RelayRoomAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            relayRoomEventPublisher.publishPartAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
            RelayRoomEventLogger.websocketBusiness("relay_part_auto_submitted",
                metadata("room_id", autoSubmission.roomCode(), "uuid", autoSubmission.assignment().assignedUserUuid(),
                    "canvas_index", autoSubmission.assignment().canvasIndex(), "part",
                    autoSubmission.assignment().part(), "reason", "timeout", "empty",
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
            relayRoomFinalizationService.triggerFinalization(result.roomCode());
        } else {
            relayRoomEventPublisher.publishPartStarted(result.roomCode(), result.previousPart(),
                advanceResult.nextPart(), advanceResult.nextPartStartedAt(), advanceResult.nextPartDeadlineAt());
            RelayRoomEventLogger.websocketBusiness("relay_part_started",
                metadata("room_id", result.roomCode(), "part", advanceResult.nextPart(), "previous_part",
                    result.previousPart(), "participant_count", advanceResult.roomState().participantCount(),
                    "part_deadline_at", advanceResult.nextPartDeadlineAt()));
        }
    }

    private record AutoSubmitUpdate(List<RelayRoomAssignment> updatedAssignments,
        List<RelayRoomAutoSubmissionResult> autoSubmissions) {
    }
}
