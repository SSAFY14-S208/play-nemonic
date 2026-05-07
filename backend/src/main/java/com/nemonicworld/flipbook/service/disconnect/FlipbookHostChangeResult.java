package com.nemonicworld.flipbook.service.disconnect;

import java.time.LocalDateTime;

/**
 * 게임 중 방장 이탈 확정으로 발생한 플립북 방장 승계 결과입니다.
 */
public record FlipbookHostChangeResult(String roomCode, String previousHostUserUuid, String newHostUserUuid,
    String newHostNickname, LocalDateTime changedAt) {
}
