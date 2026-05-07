package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.close.RelayRoomCloseResult;
import java.time.LocalDateTime;

/**
 * 릴레이 방 종료 결과를 반환합니다.
 */
public record RelayRoomCloseResponse(String roomCode, RelayRoomStatus roomStatus, LocalDateTime closedAt,
    boolean alreadyClosed) {

    public static RelayRoomCloseResponse closed(RelayRoomCloseResult closeResult) {
        return new RelayRoomCloseResponse(closeResult.roomCode(), RelayRoomStatus.CLOSED, closeResult.closedAt(),
            false);
    }

    public static RelayRoomCloseResponse alreadyClosed(RelayRoomState roomState) {
        return new RelayRoomCloseResponse(roomState.roomCode(), RelayRoomStatus.CLOSED, roomState.updatedAt(), true);
    }
}
