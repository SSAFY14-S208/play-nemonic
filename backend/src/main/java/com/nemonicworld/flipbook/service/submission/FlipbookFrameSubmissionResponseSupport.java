package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoundProgress;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class FlipbookFrameSubmissionResponseSupport {

    private final FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;

    public FlipbookFrameSubmissionResponseSupport(FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService) {
        this.flipbookFrameImageUrlResolver = flipbookFrameImageUrlResolver;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
    }

    public FlipbookFrameSubmitResponse createResponse(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        boolean alreadySubmitted, FlipbookRoomParticipant participant) {
        FlipbookRoundProgress progress = flipbookRoomRoundAdvanceService.calculateProgress(roomState,
            assignment.round());
        boolean allRoundsCompleted = roomState.status() == FlipbookRoomStatus.FINALIZING
            || roomState.status() == FlipbookRoomStatus.FINISHED;
        boolean advanced = allRoundsCompleted
            || roomState.currentRound() != null && roomState.currentRound() > assignment.round();
        Integer nextRound = advanced && roomState.status() == FlipbookRoomStatus.PLAYING
            ? roomState.currentRound()
            : null;
        LocalDateTime nextRoundStartedAt = nextRound == null ? null : roomState.roundStartedAt();
        LocalDateTime nextRoundDeadlineAt = nextRound == null ? null : roomState.roundDeadlineAt();

        return createResponse(roomState, assignment, alreadySubmitted, participant, new FlipbookRoundAdvanceResult(
            roomState, advanced, nextRound, nextRoundStartedAt, nextRoundDeadlineAt, allRoundsCompleted, progress));
    }

    public FlipbookFrameSubmitResponse createResponse(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        boolean alreadySubmitted, FlipbookRoomParticipant participant, FlipbookRoundAdvanceResult advanceResult) {
        String frameUrl = flipbookFrameImageUrlResolver.resolve(assignment.objectKey());

        return FlipbookFrameSubmitResponse.from(roomState.roomCode(), assignment, frameUrl, alreadySubmitted,
            advanceResult.progress().currentRoundCompleted(), advanceResult.progress().submittedCount(),
            advanceResult.progress().totalCount(), advanceResult.advanced(), advanceResult.nextRound(),
            advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt(), advanceResult.allRoundsCompleted(),
            roomState.status(), participant.userUuid(), participant.nickname());
    }
}
