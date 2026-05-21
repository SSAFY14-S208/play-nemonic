package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.LocalDateTime;

/**
 * FINISHED 이후 결과 확인 시간이 지난 방이 CLOSED로 전환되었음을 알리는 이벤트 payload입니다.
 */
public record RelayRoomClosedEventResponse(String roomCode, RelayRoomStatus roomStatus, LocalDateTime closedAt,
    String closeReason) {
}
