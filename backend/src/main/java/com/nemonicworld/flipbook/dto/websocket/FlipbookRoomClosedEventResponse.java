package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.LocalDateTime;

/**
 * 마지막 참여자 퇴장으로 플립북 대기방이 CLOSED로 전환되었음을 알리는 이벤트 payload입니다.
 */
public record FlipbookRoomClosedEventResponse(String roomCode, FlipbookRoomStatus roomStatus, LocalDateTime closedAt) {
}
