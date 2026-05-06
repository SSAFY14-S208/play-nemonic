package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.entity.RelayRoomState;
import java.time.LocalDateTime;

/**
 * FINISHED 방 하나를 CLOSED로 전환한 결과입니다.
 */
public record RelayRoomCloseResult(String roomCode, boolean closed, RelayRoomState roomState, LocalDateTime closedAt) {

    public static RelayRoomCloseResult closed(RelayRoomState roomState, LocalDateTime closedAt) {
        return new RelayRoomCloseResult(roomState.roomCode(), true, roomState, closedAt);
    }

    public static RelayRoomCloseResult noOp(String roomCode) {
        return new RelayRoomCloseResult(roomCode, false, null, null);
    }
}
