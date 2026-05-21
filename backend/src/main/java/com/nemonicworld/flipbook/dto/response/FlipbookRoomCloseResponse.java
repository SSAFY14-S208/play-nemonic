package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseResult;
import java.time.LocalDateTime;

/**
 * 플립북 방 종료 결과를 반환합니다.
 */
public record FlipbookRoomCloseResponse(String roomCode, FlipbookRoomStatus roomStatus, LocalDateTime closedAt,
    boolean alreadyClosed) {

    public static FlipbookRoomCloseResponse closed(FlipbookRoomCloseResult closeResult) {
        return new FlipbookRoomCloseResponse(closeResult.roomCode(), FlipbookRoomStatus.CLOSED, closeResult.closedAt(),
            false);
    }

    public static FlipbookRoomCloseResponse alreadyClosed(FlipbookRoomState roomState) {
        return new FlipbookRoomCloseResponse(roomState.roomCode(), FlipbookRoomStatus.CLOSED, roomState.updatedAt(),
            true);
    }
}
