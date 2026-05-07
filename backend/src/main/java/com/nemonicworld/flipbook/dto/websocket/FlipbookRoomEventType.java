package com.nemonicworld.flipbook.dto.websocket;

/**
 * 플립북 WebSocket으로 클라이언트에 전달하는 이벤트 종류입니다.
 */
public enum FlipbookRoomEventType {
    // 참여자의 WebSocket 연결이 활성화되었음을 알립니다.
    PARTICIPANT_CONNECTED,

    // 참여자의 WebSocket 연결이 해제되었음을 알립니다.
    PARTICIPANT_DISCONNECTED,

    // 방 설정이 변경되었음을 알립니다.
    SETTINGS_CHANGED,

    // 같은 UUID의 기존 세션이 중복 접속으로 종료되었음을 알립니다.
    DUPLICATE_SESSION_CLOSED,

    // heartbeat ping에 대한 응답입니다.
    PONG,

    // WebSocket 처리 중 안전하게 전달 가능한 오류입니다.
    ERROR
}
