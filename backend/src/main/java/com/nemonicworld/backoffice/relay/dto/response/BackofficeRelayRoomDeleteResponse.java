package com.nemonicworld.backoffice.relay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 릴레이 드로잉 방 삭제 응답")
public record BackofficeRelayRoomDeleteResponse(
    @Schema(description = "삭제한 공유 방코드", example = "AB3K9Q") String roomCode) {
}
