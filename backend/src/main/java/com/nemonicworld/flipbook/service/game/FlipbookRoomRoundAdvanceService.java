package com.nemonicworld.flipbook.service.game;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 플립북 라운드 완료 여부를 계산하고 다음 라운드 또는 종료 상태로 전환합니다.
 */
@Component
public class FlipbookRoomRoundAdvanceService {

    /**
     * 완료된 라운드를 기준으로 다음 라운드 또는 게임 종료 상태로 전환합니다.
     */
    public FlipbookRoundAdvanceResult advanceRoundIfCompleted(FlipbookRoomState roomState, int completedRound,
        LocalDateTime now) {
        FlipbookRoundProgress progress = calculateProgress(roomState, completedRound);
        if (!progress.currentRoundCompleted()) {
            return FlipbookRoundAdvanceResult.notAdvanced(roomState, progress);
        }

        if (roomState.totalRounds() == null || completedRound >= roomState.totalRounds()) {
            FlipbookRoomState finishedRoomState = roomState.finishGame(roomState.assignments(), now);

            return FlipbookRoundAdvanceResult.allRoundsCompleted(finishedRoomState, progress);
        }

        int nextRound = completedRound + 1;
        FlipbookRoomState nextRoundRoomState = roomState.startNextRound(nextRound, roomState.assignments(), now);

        return FlipbookRoundAdvanceResult.advanced(nextRoundRoomState, nextRound, progress);
    }

    /**
     * 특정 라운드의 전체 배정 수와 제출 완료 수를 계산합니다.
     */
    public FlipbookRoundProgress calculateProgress(FlipbookRoomState roomState, int round) {
        int totalCount = 0;
        int submittedCount = 0;

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() != round) {
                continue;
            }

            totalCount++;
            if (isCompleted(assignment)) {
                submittedCount++;
            }
        }

        return new FlipbookRoundProgress(submittedCount, totalCount, totalCount > 0 && submittedCount == totalCount);
    }

    /**
     * 수동 제출과 자동 제출을 모두 제출 완료 상태로 봅니다.
     */
    boolean isCompleted(FlipbookFrameAssignment assignment) {
        return assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED
            || assignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()
            || assignment.empty();
    }
}
