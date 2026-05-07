package com.nemonicworld.relay.service.disconnect;

import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.timeout.RelayRoomAutoSubmissionResult;
import java.util.List;

/**
 * 릴레이 방 1개의 게임 중 이탈 확정 처리 결과입니다.
 */
public record RelayDisconnectGraceRoomResult(String roomCode, boolean processed,
    List<RelayDroppedParticipantResult> droppedParticipants, RelayHostChangeResult hostChange,
    List<RelayRoomAutoSubmissionResult> autoSubmissions, RelayPartAdvanceResult advanceResult) {

    public RelayDisconnectGraceRoomResult {
        droppedParticipants = droppedParticipants == null ? List.of() : List.copyOf(droppedParticipants);
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }

    public static RelayDisconnectGraceRoomResult noOp(String roomCode) {
        return new RelayDisconnectGraceRoomResult(roomCode, false, List.of(), null, List.of(), null);
    }
}
