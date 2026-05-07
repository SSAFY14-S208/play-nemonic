package com.nemonicworld.flipbook.service.game;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.LocalDateTime;

/**
 * 플립북 라운드 완료 후 다음 단계 전환 결과를 담습니다.
 */
public record FlipbookRoundAdvanceResult(FlipbookRoomState roomState, boolean advanced, Integer nextRound,
    LocalDateTime nextRoundStartedAt, LocalDateTime nextRoundDeadlineAt, boolean allRoundsCompleted,
    FlipbookRoundProgress progress) {

    public static FlipbookRoundAdvanceResult notAdvanced(FlipbookRoomState roomState, FlipbookRoundProgress progress) {
        return new FlipbookRoundAdvanceResult(roomState, false, null, null, null, false, progress);
    }

    public static FlipbookRoundAdvanceResult advanced(FlipbookRoomState roomState, int nextRound,
        FlipbookRoundProgress progress) {
        return new FlipbookRoundAdvanceResult(roomState, true, nextRound, roomState.roundStartedAt(),
            roomState.roundDeadlineAt(), false, progress);
    }

    public static FlipbookRoundAdvanceResult allRoundsCompleted(FlipbookRoomState roomState,
        FlipbookRoundProgress progress) {
        return new FlipbookRoundAdvanceResult(roomState, true, null, null, null, true, progress);
    }
}
