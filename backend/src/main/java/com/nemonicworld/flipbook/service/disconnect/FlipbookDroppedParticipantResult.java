package com.nemonicworld.flipbook.service.disconnect;

import java.time.LocalDateTime;

/**
 * 재접속 유예가 만료되어 이탈 확정된 플립북 참여자 정보입니다.
 */
public record FlipbookDroppedParticipantResult(String roomCode, String userUuid, String nickname,
    LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
}
