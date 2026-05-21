package com.nemonicworld.flipbook.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 플립북 대기실 참여자 강퇴 요청입니다.
 */
public record FlipbookRoomKickRequest(
    @Schema(description = "강퇴 대상 사용자 UUID", example = "11111111-1111-1111-1111-111111111111") String targetUserUuid) {
}
