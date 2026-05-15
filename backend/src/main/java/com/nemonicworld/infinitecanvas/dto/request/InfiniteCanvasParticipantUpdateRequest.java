package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 내 참여자 프로필 수정 요청")
public record InfiniteCanvasParticipantUpdateRequest(@Schema(description = "참여자 닉네임", nullable = true) String nickname,
    @Schema(description = "참여자 색상", nullable = true, example = "#2F80ED") String color,
    @Schema(description = "참여자 아바타 URL", nullable = true) String avatarUrl) {
}
