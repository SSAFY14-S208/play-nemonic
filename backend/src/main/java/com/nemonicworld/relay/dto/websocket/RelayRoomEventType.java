package com.nemonicworld.relay.dto.websocket;

/**
 * 릴레이 WebSocket으로 클라이언트에 전달하는 이벤트 종류입니다.
 */
public enum RelayRoomEventType {
    ROOM_UPDATED, SETTINGS_CHANGED, PARTICIPANT_CONNECTED, PARTICIPANT_DISCONNECTED, DUPLICATE_SESSION_CLOSED, PONG, ERROR
}
