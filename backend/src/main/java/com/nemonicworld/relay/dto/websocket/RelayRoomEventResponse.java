package com.nemonicworld.relay.dto.websocket;

import java.time.LocalDateTime;

/**
 * 릴레이 WebSocket 이벤트의 공통 wrapper 응답입니다.
 */
public record RelayRoomEventResponse(RelayRoomEventType type, String roomCode, Object data, LocalDateTime occurredAt) {

    /**
     * 이벤트 발생 시각을 서버 기준 현재 시각으로 넣어 wrapper를 생성합니다.
     */
    public static RelayRoomEventResponse of(RelayRoomEventType type, String roomCode, Object data) {
        return new RelayRoomEventResponse(type, roomCode, data, LocalDateTime.now());
    }
}
