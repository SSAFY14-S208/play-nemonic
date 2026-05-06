package com.nemonicworld.relay.dto.websocket;

/**
 * 릴레이 WebSocket으로 클라이언트에 전달하는 이벤트 종류입니다.
 */
public enum RelayRoomEventType {
    // 방 상태가 명시적으로 갱신되었음을 알립니다.
    ROOM_UPDATED,

    // 방 설정이 변경되었음을 알립니다.
    SETTINGS_CHANGED,

    // 릴레이 게임이 시작되었음을 알립니다.
    GAME_STARTED,

    // 현재 파트가 시작되었음을 알립니다.
    PART_STARTED,

    PART_SUBMITTED,

    PART_AUTO_SUBMITTED,

    ALL_PARTS_COMPLETED,

    RESULT_CREATED,

    // 결과 확인 시간이 지나 방 런타임이 종료되었음을 알립니다.
    ROOM_CLOSED,

    // 참여자의 WebSocket 연결이 활성화되었음을 알립니다.
    PARTICIPANT_CONNECTED,

    // 참여자의 WebSocket 연결이 해제되었음을 알립니다.
    PARTICIPANT_DISCONNECTED,

    // 방장이 대기실 참여자를 강퇴했음을 알립니다.
    PARTICIPANT_KICKED,

    // 대기실 참여자가 스스로 퇴장했음을 알립니다.
    PARTICIPANT_LEFT,

    // 대기실 방장이 퇴장해 새 방장에게 승계되었음을 알립니다.
    HOST_CHANGED,

    // 강퇴 대상자 개인 큐로 전달하는 강퇴 안내입니다.
    KICKED_FROM_ROOM,

    // 같은 UUID의 기존 세션이 중복 접속으로 종료되었음을 알립니다.
    DUPLICATE_SESSION_CLOSED,

    // heartbeat ping에 대한 응답입니다.
    PONG,

    // WebSocket 처리 중 안전하게 전달 가능한 오류입니다.
    ERROR;
}
