package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import java.util.List;

/**
 * 플립북 방 하나의 타임아웃 자동 제출 처리 결과입니다.
 */
public record FlipbookRoomTimeoutResult(String roomCode, boolean processed, Integer previousRound,
    List<FlipbookFrameAutoSubmissionResult> autoSubmissions, FlipbookRoundAdvanceResult advanceResult) {

    public FlipbookRoomTimeoutResult {
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }

    static FlipbookRoomTimeoutResult noOp(String roomCode) {
        return new FlipbookRoomTimeoutResult(roomCode, false, null, List.of(), null);
    }
}
