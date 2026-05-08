package com.nemonicworld.relay.entity;

/**
 * 릴레이 방의 진행 상태입니다.
 */
public enum RelayRoomStatus {
    // 방 생성 직후, 게임 시작 전 대기 상태입니다.
    WAITING,

    // 게임이 시작되어 파트별 그림 제출이 진행 중인 상태입니다.
    PLAYING,

    // 모든 파트 제출이 끝났고, canvasIndex별 최종 결과물 생성과 갤러리 지급을 기다리는 상태입니다.
    FINALIZING,

    // 게임 결과 생성이 완료된 상태입니다.
    FINISHED,

    // 방이 명시적으로 종료되었거나 더 이상 사용할 수 없는 상태입니다.
    CLOSED
}
