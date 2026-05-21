package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 AI 스티커 생성 요청")
public record InfiniteCanvasAiStickerCreateRequest(
    @Schema(description = "사용자가 입력한 스티커 생성 프롬프트", example = "바이올린을 켜는 토끼") String prompt,
    @Schema(description = "스티커 스타일", example = "sticker", nullable = true) String style,
    @Schema(description = "생성 이미지 너비", example = "512", nullable = true) Integer width,
    @Schema(description = "생성 이미지 높이", example = "512", nullable = true) Integer height,
    @Schema(description = "투명 배경 선호 여부", example = "true", nullable = true) Boolean transparentBackground) {
}
