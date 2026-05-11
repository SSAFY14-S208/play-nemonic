package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundTimeUpEventResponse.PendingSubmission;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomTimeUpNotificationRepository;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
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
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 마감 시간이 지난 플립북 현재 라운드의 미제출 프레임을 빈 프레임으로 자동 제출합니다.
 */
@Service
public class FlipbookRoomTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomTimeoutService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomTimeUpNotificationRepository flipbookRoomTimeUpNotificationRepository;
    private final FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;
    private final FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final int scanLimit;
    private final Duration autoSubmitGrace;
    private final Duration roomMutationLockTtl;

    public FlipbookRoomTimeoutService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomTimeUpNotificationRepository flipbookRoomTimeUpNotificationRepository,
        FlipbookSubmissionLockRepository flipbookSubmissionLockRepository,
        FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        @Value("${nemonic.flipbook.timeout.scan-limit:100}") int scanLimit,
        @Value("${nemonic.flipbook.timeout.auto-submit-grace-ms:5000}") long autoSubmitGraceMs,
        @Value("${nemonic.flipbook.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomTimeUpNotificationRepository = flipbookRoomTimeUpNotificationRepository;
        this.flipbookSubmissionLockRepository = flipbookSubmissionLockRepository;
        this.flipbookRoomMutationLockRepository = flipbookRoomMutationLockRepository;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.scanLimit = scanLimit;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    /**
     * Redis에서 마감 시간이 지난 PLAYING 방을 찾아 자동 제출을 처리합니다.
     */
    public FlipbookTimeoutProcessResult processExpiredRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        publishRoundTimeUpEvents(flipbookRoomRepository.findExpiredPlayingRooms(now, scanLimit), now);
        LocalDateTime autoSubmitCutoff = now.minus(autoSubmitGrace);
        List<FlipbookRoomState> expiredRooms = flipbookRoomRepository.findExpiredPlayingRooms(autoSubmitCutoff,
            scanLimit);
        int processedRoomCount = 0;
        int autoSubmittedCount = 0;

        for (FlipbookRoomState expiredRoom : expiredRooms) {
            try {
                FlipbookRoomTimeoutResult result = processExpiredRoom(expiredRoom.roomCode(), now);
                if (result.processed()) {
                    processedRoomCount++;
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                log.warn("플립북 타임아웃 자동 제출 처리 중 오류가 발생했습니다. roomCode={}", expiredRoom.roomCode(), e);
                FlipbookRoomEventLogger.apiWarn("flipbook_timeout_scheduler_failed",
                    "failed to process flipbook timeout room", metadata("room_id", expiredRoom.roomCode()), e);
            }
        }

        return new FlipbookTimeoutProcessResult(expiredRooms.size(), processedRoomCount, autoSubmittedCount);
    }

    private void publishRoundTimeUpEvents(List<FlipbookRoomState> timeUpRooms, LocalDateTime now) {
        for (FlipbookRoomState roomState : timeUpRooms) {
            if (!shouldPublishRoundTimeUpEvent(roomState, now)) {
                continue;
            }

            try {
                boolean marked = flipbookRoomTimeUpNotificationRepository.markRoundTimeUpNotified(roomState.roomCode(),
                    roomState.currentRound(), roomState.roundDeadlineAt(), FlipbookRoomRepository.ROOM_STATE_TTL);
                if (marked) {
                    LocalDateTime submitGraceDeadlineAt = roomState.roundDeadlineAt().plus(autoSubmitGrace);
                    List<PendingSubmission> pendingSubmissions = pendingCurrentSubmissions(roomState);
                    flipbookRoomEventPublisher.publishRoundTimeUp(roomState.roomCode(), roomState.currentRound(),
                        roomState.roundDeadlineAt(), submitGraceDeadlineAt, autoSubmitGrace.toMillis(),
                        pendingSubmissions);
                    FlipbookRoomEventLogger.websocketBusiness("flipbook_round_time_up",
                        metadata("room_id", roomState.roomCode(), "round", roomState.currentRound(),
                            "round_deadline_at", roomState.roundDeadlineAt(), "pending_count",
                            pendingSubmissions.size(), "auto_submit_grace_ms", autoSubmitGrace.toMillis()));
                }
            } catch (RuntimeException e) {
                log.warn("플립북 라운드 제한 시간 종료 이벤트 발행 중 오류가 발생했습니다. roomCode={}, round={}", roomState.roomCode(),
                    roomState.currentRound(), e);
                FlipbookRoomEventLogger.websocketWarn("flipbook_round_time_up_publish_failed",
                    "failed to publish flipbook round time-up event",
                    metadata("room_id", roomState.roomCode(), "round", roomState.currentRound()), e);
            }
        }
    }

    /**
     * 지정한 방의 현재 라운드가 만료되었으면 PENDING 배정을 AUTO_SUBMITTED로 바꿉니다.
     */
    public FlipbookRoomTimeoutResult processExpiredRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState candidateRoomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (!isExpiredPlayingRoom(candidateRoomState, processedAt)) {
            return FlipbookRoomTimeoutResult.noOp(roomCode);
        }

        String roomMutationLockToken = createRoomMutationLockToken("timeout", roomCode);
        boolean locked = flipbookRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
            roomMutationLockTtl);
        if (!locked) {
            log.warn("플립북 타임아웃 자동 제출을 건너뜁니다. 방 상태 변경 잠금이 사용 중입니다. roomCode={}", roomCode);
            FlipbookRoomEventLogger.apiWarn("flipbook_room_mutation_lock_busy",
                "flipbook timeout skipped because room mutation lock is busy",
                metadata("room_id", roomCode, "lock_owner", "timeout"), null);
            return FlipbookRoomTimeoutResult.noOp(roomCode);
        }

        try {
            FlipbookRoomTimeoutResult result = processExpiredRoomWithLock(roomCode, processedAt);
            publishTimeoutEvents(result);

            return result;
        } finally {
            flipbookRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
        }
    }

    private FlipbookRoomTimeoutResult processExpiredRoomWithLock(String roomCode, LocalDateTime processedAt) {
        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (!isExpiredPlayingRoom(roomState, processedAt)) {
                return FlipbookRoomTimeoutResult.noOp(roomCode);
            }

            Integer currentRound = roomState.currentRound();
            AutoSubmitUpdate autoSubmitUpdate = autoSubmitPendingAssignments(roomState, currentRound, processedAt);
            FlipbookRoomState submittedRoomState = autoSubmitUpdate.autoSubmissions().isEmpty()
                ? roomState
                : roomState.withAssignments(autoSubmitUpdate.updatedAssignments(), processedAt);
            FlipbookRoundAdvanceResult advanceResult = flipbookRoomRoundAdvanceService
                .advanceRoundIfCompleted(submittedRoomState, currentRound, processedAt);

            if (autoSubmitUpdate.autoSubmissions().isEmpty() && !advanceResult.advanced()) {
                return FlipbookRoomTimeoutResult.noOp(roomCode);
            }

            if (flipbookRoomRepository.saveIfUnchanged(roomState, advanceResult.roomState())) {
                flipbookInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                return new FlipbookRoomTimeoutResult(roomCode, true, currentRound, autoSubmitUpdate.autoSubmissions(),
                    advanceResult);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_TIMEOUT_UPDATE_CONFLICT_MESSAGE);
    }

    private String createRoomMutationLockToken(String owner, String roomCode) {
        return "token=%s,requestedAt=%s,owner=%s,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, roomCode);
    }

    private boolean isExpiredPlayingRoom(FlipbookRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == FlipbookRoomStatus.PLAYING && roomState.currentRound() != null
            && roomState.roundDeadlineAt() != null && !roomState.roundDeadlineAt().plus(autoSubmitGrace).isAfter(now);
    }

    private boolean shouldPublishRoundTimeUpEvent(FlipbookRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == FlipbookRoomStatus.PLAYING && roomState.currentRound() != null
            && roomState.roundDeadlineAt() != null && !roomState.roundDeadlineAt().isAfter(now)
            && roomState.roundDeadlineAt().plus(autoSubmitGrace).isAfter(now) && hasPendingCurrentAssignment(roomState);
    }

    private boolean hasPendingCurrentAssignment(FlipbookRoomState roomState) {
        return roomState.assignments().stream().anyMatch(assignment -> assignment.round() == roomState.currentRound()
            && assignment.status() == FlipbookFrameAssignmentStatus.PENDING);
    }

    private List<PendingSubmission> pendingCurrentSubmissions(FlipbookRoomState roomState) {
        return roomState.assignments().stream()
            .filter(assignment -> assignment.round() == roomState.currentRound()
                && assignment.status() == FlipbookFrameAssignmentStatus.PENDING)
            .sorted(Comparator.comparingInt(FlipbookFrameAssignment::flipbookIndex)
                .thenComparingInt(FlipbookFrameAssignment::frameIndex))
            .map(assignment -> {
                FlipbookRoomParticipant participant = findParticipant(roomState, assignment.assignedUserUuid());
                return new PendingSubmission(assignment.flipbookIndex(), assignment.frameIndex(),
                    assignment.assignedUserUuid(), participant == null ? null : participant.nickname(),
                    participant != null && participant.connected());
            }).toList();
    }

    private FlipbookRoomParticipant findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst().orElse(null);
    }

    private AutoSubmitUpdate autoSubmitPendingAssignments(FlipbookRoomState roomState, int currentRound,
        LocalDateTime submittedAt) {
        List<FlipbookFrameAssignment> updatedAssignments = new ArrayList<>(roomState.assignments().size());
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() == currentRound && assignment.status() == FlipbookFrameAssignmentStatus.PENDING
                && !isSubmissionLocked(roomState, assignment)) {
                FlipbookFrameAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                updatedAssignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new FlipbookFrameAutoSubmissionResult(roomState.roomCode(),
                    findNickname(roomState, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return new AutoSubmitUpdate(updatedAssignments, autoSubmissions);
    }

    private FlipbookFrameAssignment autoSubmitAssignment(FlipbookFrameAssignment assignment,
        LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(),
            assignment.assignedUserUuid(), FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, true, true,
            submittedAt);
    }

    private boolean isSubmissionLocked(FlipbookRoomState roomState, FlipbookFrameAssignment assignment) {
        return flipbookSubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.flipbookIndex(),
            assignment.frameIndex(), assignment.round(), assignment.assignedUserUuid());
    }

    private String findNickname(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
    }

    private void publishTimeoutEvents(FlipbookRoomTimeoutResult result) {
        for (FlipbookFrameAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            flipbookRoomEventPublisher.publishFrameAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_frame_auto_submitted",
                metadata("room_id", autoSubmission.roomCode(), "round", autoSubmission.assignment().round(),
                    "flipbook_index", autoSubmission.assignment().flipbookIndex(), "frame_index",
                    autoSubmission.assignment().frameIndex(), "uuid", autoSubmission.assignment().assignedUserUuid(),
                    "reason", "timeout"));
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
        } else {
            flipbookRoomEventPublisher.publishRoundStarted(result.roomCode(), result.previousRound(),
                advanceResult.nextRound(), advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_round_started",
                metadata("room_id", result.roomCode(), "previous_round", result.previousRound(), "round",
                    advanceResult.nextRound(), "round_deadline_at", advanceResult.nextRoundDeadlineAt()));
        }
    }

    private record AutoSubmitUpdate(List<FlipbookFrameAssignment> updatedAssignments,
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions) {
    }
}
