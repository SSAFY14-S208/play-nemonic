package com.nemonicworld.flipbook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플립북 방 조회 요청자 상태 응답")
/**
 * 요청한 Anonymous-User-UUID 기준으로 방 참여, 입장, 시작 가능 여부를 알려줍니다.
 */
public record FlipbookRoomViewerResponse(
    @Schema(description = "조회 요청자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "현재 방 참여자 여부", example = "true") boolean participant,
    @Schema(description = "현재 방장 여부", example = "true") boolean host,
    @Schema(description = "신규 입장 가능 여부", example = "false") boolean canJoin,
    @Schema(description = "게임 시작 가능 여부", example = "true") boolean canStart,
    @Schema(description = "입장 차단 이유", example = "ROOM_FULL") FlipbookRoomViewerBlockedReason blockedReason) {
}
