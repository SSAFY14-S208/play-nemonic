package com.nemonicworld.relay.service.disconnect;

import java.time.LocalDateTime;

/**
 * 게임 중 방장 이탈 확정으로 발생한 방장 승계 결과입니다.
 */
public record RelayHostChangeResult(String roomCode, String previousHostUserUuid, String newHostUserUuid,
    String newHostNickname, LocalDateTime changedAt) {
}
