package com.nemonicworld.flipbook.service.disconnect;

import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.timeout.FlipbookFrameAutoSubmissionResult;
import java.util.List;

/**
 * 플립북 방 1개의 게임 중 이탈 확정 처리 결과입니다.
 */
public record FlipbookDisconnectGraceRoomResult(String roomCode, boolean processed,
    List<FlipbookDroppedParticipantResult> droppedParticipants, FlipbookHostChangeResult hostChange,
    List<FlipbookFrameAutoSubmissionResult> autoSubmissions, FlipbookRoundAdvanceResult advanceResult) {

    public FlipbookDisconnectGraceRoomResult {
        droppedParticipants = droppedParticipants == null ? List.of() : List.copyOf(droppedParticipants);
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }

    public static FlipbookDisconnectGraceRoomResult noOp(String roomCode) {
        return new FlipbookDisconnectGraceRoomResult(roomCode, false, List.of(), null, List.of(), null);
    }
}
