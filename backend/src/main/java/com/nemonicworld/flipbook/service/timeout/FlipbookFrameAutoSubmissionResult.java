package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;

/**
 * 마감 시간으로 자동 제출된 플립북 프레임 정보를 담습니다.
 */
public record FlipbookFrameAutoSubmissionResult(String roomCode, String nickname, FlipbookFrameAssignment assignment) {
}
