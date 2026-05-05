package com.nemonicworld.relay.service;

import com.nemonicworld.relay.entity.RelayRoomAssignment;

/**
 * 타임아웃으로 자동 제출된 배정과 이벤트 표시 정보를 담습니다.
 */
public record RelayRoomAutoSubmissionResult(String roomCode, String nickname, RelayRoomAssignment assignment) {
}
