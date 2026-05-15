package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.LocalDateTime;

/**
 * 플립북 방이 CLOSED로 전환되었음을 알리는 이벤트 payload입니다.
 */
public record FlipbookRoomClosedEventResponse(String roomCode, FlipbookRoomStatus roomStatus, LocalDateTime closedAt,
    String closeReason) {
}
