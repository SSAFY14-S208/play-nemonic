package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 마감 시간이 지난 플립북 현재 라운드의 미제출 프레임을 빈 프레임으로 자동 제출합니다.
 */
@Service
public class FlipbookRoomTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomTimeoutService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final int scanLimit;
    private final Duration autoSubmitGrace;

    public FlipbookRoomTimeoutService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService,
        FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        @Value("${nemonic.flipbook.timeout.scan-limit:100}") int scanLimit,
        @Value("${nemonic.flipbook.timeout.auto-submit-grace-ms:2000}") long autoSubmitGraceMs) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.scanLimit = scanLimit;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
    }

    /**
     * Redis에서 마감 시간이 지난 PLAYING 방을 찾아 자동 제출을 처리합니다.
     */
    public FlipbookTimeoutProcessResult processExpiredRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
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
            }
        }

        return new FlipbookTimeoutProcessResult(expiredRooms.size(), processedRoomCount, autoSubmittedCount);
    }

    /**
     * 지정한 방의 현재 라운드가 만료되었으면 PENDING 배정을 AUTO_SUBMITTED로 바꿉니다.
     */
    public FlipbookRoomTimeoutResult processExpiredRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);

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
                FlipbookRoomTimeoutResult result = new FlipbookRoomTimeoutResult(roomCode, true, currentRound,
                    autoSubmitUpdate.autoSubmissions(), advanceResult);
                publishTimeoutEvents(result);

                return result;
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_TIMEOUT_UPDATE_CONFLICT_MESSAGE);
    }

    private boolean isExpiredPlayingRoom(FlipbookRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == FlipbookRoomStatus.PLAYING && roomState.currentRound() != null
            && roomState.roundDeadlineAt() != null && !roomState.roundDeadlineAt().plus(autoSubmitGrace).isAfter(now);
    }

    private AutoSubmitUpdate autoSubmitPendingAssignments(FlipbookRoomState roomState, int currentRound,
        LocalDateTime submittedAt) {
        List<FlipbookFrameAssignment> updatedAssignments = new ArrayList<>(roomState.assignments().size());
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() == currentRound && assignment.status() == FlipbookFrameAssignmentStatus.PENDING) {
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

    private String findNickname(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
    }

    private void publishTimeoutEvents(FlipbookRoomTimeoutResult result) {
        for (FlipbookFrameAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            flipbookRoomEventPublisher.publishFrameAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
        }

        FlipbookRoundAdvanceResult advanceResult = result.advanceResult();
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allRoundsCompleted()) {
            flipbookRoomEventPublisher.publishAllRoundsCompleted(result.roomCode(), advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
        } else {
            flipbookRoomEventPublisher.publishRoundStarted(result.roomCode(), result.previousRound(),
                advanceResult.nextRound(), advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt());
        }
    }

    private record AutoSubmitUpdate(List<FlipbookFrameAssignment> updatedAssignments,
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions) {
    }
}
