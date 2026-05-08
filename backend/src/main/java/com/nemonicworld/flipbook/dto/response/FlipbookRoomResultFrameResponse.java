package com.nemonicworld.flipbook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 플립북 결과를 구성하는 개별 프레임 정보입니다.
 */
@Schema(description = "플립북 결과 프레임")
public record FlipbookRoomResultFrameResponse(@Schema(description = "프레임 번호", example = "0") int frameIndex,
    @Schema(description = "프레임 이미지 URL") String imageUrl, @Schema(description = "그린 사용자 UUID") String drawnByUserUuid,
    @Schema(description = "그린 사용자 닉네임", example = "망고") String drawnByNickname) {
}
