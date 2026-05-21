package com.nemonicworld.backoffice.flipbook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 플립북 방 삭제 응답")
public record BackofficeFlipbookRoomDeleteResponse(
    @Schema(description = "삭제한 공유 방코드", example = "AB3K9Q") String roomCode) {
}
