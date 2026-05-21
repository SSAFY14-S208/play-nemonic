package com.nemonicworld.relay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 릴레이 대기실 참여자 강퇴 요청입니다.
 */
public record RelayRoomKickRequest(
    @Schema(description = "강퇴 대상 사용자 UUID", example = "11111111-1111-1111-1111-111111111111") String targetUserUuid) {
}
