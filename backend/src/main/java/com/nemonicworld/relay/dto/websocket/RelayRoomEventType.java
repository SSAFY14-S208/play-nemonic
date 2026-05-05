package com.nemonicworld.relay.dto.websocket;

/**
 * 릴레이 WebSocket으로 클라이언트에 전달하는 이벤트 종류입니다.
 */
public enum RelayRoomEventType {
    // 방 상태가 명시적으로 갱신되었음을 알립니다.
    ROOM_UPDATED,

    // 방 설정이 변경되었음을 알립니다.
    SETTINGS_CHANGED,

    // 참여자의 WebSocket 연결이 활성화되었음을 알립니다.
    PARTICIPANT_CONNECTED,

    // 참여자의 WebSocket 연결이 해제되었음을 알립니다.
    PARTICIPANT_DISCONNECTED,

    // 같은 UUID의 기존 세션이 중복 접속으로 종료되었음을 알립니다.
    DUPLICATE_SESSION_CLOSED,

    // heartbeat ping에 대한 응답입니다.
    PONG,

    // WebSocket 처리 중 안전하게 전달 가능한 오류입니다.
    ERROR;
}
