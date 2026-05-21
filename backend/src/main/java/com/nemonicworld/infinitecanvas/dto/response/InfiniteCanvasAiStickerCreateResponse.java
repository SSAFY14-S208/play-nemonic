package com.nemonicworld.infinitecanvas.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 AI 스티커 생성 응답")
public record InfiniteCanvasAiStickerCreateResponse(@Schema(description = "생성된 스티커 ID") String stickerId,
    @Schema(description = "브라우저에서 렌더링할 수 있는 이미지 URL") String imageUrl,
    @Schema(description = "MinIO 객체 키") String objectKey,
    @Schema(description = "이미지 Content-Type", example = "image/png") String contentType,
    @Schema(description = "원본 생성 너비") int width, @Schema(description = "원본 생성 높이") int height,
    @Schema(description = "무한 캔버스 image element 초안") JsonNode element) {
}
