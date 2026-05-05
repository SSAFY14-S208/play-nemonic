package com.nemonicworld.relay.service;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.util.List;

/**
 * 릴레이 방 하나의 타임아웃 자동 제출 처리 결과를 담습니다.
 */
public record RelayRoomTimeoutResult(String roomCode, boolean processed, RelayDrawingPart previousPart,
    List<RelayRoomAutoSubmissionResult> autoSubmissions, RelayPartAdvanceResult advanceResult) {

    public RelayRoomTimeoutResult {
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }

    static RelayRoomTimeoutResult noOp(String roomCode) {
        return new RelayRoomTimeoutResult(roomCode, false, null, List.of(), null);
    }
}
