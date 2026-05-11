package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.LocalDateTime;

/**
 * FINISHED 플립북 방 하나를 CLOSED로 전환한 결과입니다.
 */
public record FlipbookRoomCloseResult(String roomCode, boolean closed, FlipbookRoomState roomState,
    LocalDateTime closedAt) {

    public static FlipbookRoomCloseResult closed(FlipbookRoomState roomState, LocalDateTime closedAt) {
        return new FlipbookRoomCloseResult(roomState.roomCode(), true, roomState, closedAt);
    }

    public static FlipbookRoomCloseResult noOp(String roomCode) {
        return new FlipbookRoomCloseResult(roomCode, false, null, null);
    }
}
