package com.nemonicworld.flipbook.dto.websocket;

/**
 * 플립북 WebSocket으로 클라이언트에 전달하는 이벤트 종류입니다.
 */
public enum FlipbookRoomEventType {
    // 참여자의 WebSocket 연결이 활성화되었음을 알립니다.
    PARTICIPANT_CONNECTED,

    // 참여자의 WebSocket 연결이 해제되었음을 알립니다.
    PARTICIPANT_DISCONNECTED,

    // 참여자의 재접속 유예가 만료되어 이탈 확정되었음을 알립니다.
    PARTICIPANT_DROPPED,

    // 방 설정이 변경되었음을 알립니다.
    SETTINGS_CHANGED,

    // 방장이 게임을 시작했음을 알립니다.
    GAME_STARTED,

    // 참여자가 현재 라운드 프레임을 제출했음을 알립니다.
    FRAME_SUBMITTED,

    // 현재 라운드 제한 시간이 끝나 클라이언트 자동 제출이 필요함을 알립니다.
    ROUND_TIME_UP,

    // 마감 시간으로 현재 라운드 프레임이 자동 제출되었음을 알립니다.
    FRAME_AUTO_SUBMITTED,

    // 다음 라운드가 시작되었음을 알립니다.
    ROUND_STARTED,

    // 모든 라운드가 완료되었음을 알립니다.
    ALL_ROUNDS_COMPLETED,

    // 최종 GIF 결과 생성이 완료되었음을 알립니다.
    RESULT_CREATED,

    // 마지막 참여자 퇴장으로 방이 종료되었음을 알립니다.
    ROOM_CLOSED,

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
    ERROR
}
