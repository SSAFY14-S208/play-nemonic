package com.nemonicworld.infinitecanvas.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "무한 캔버스 출력 이미지 저장 응답")
public record InfiniteCanvasOutputSaveResponse(@Schema(description = "갤러리 항목 ID") String galleryId,
    @Schema(description = "산출물 ID") String artifactId,
    @Schema(description = "산출물 종류", example = "infinite_canvas") String kind,
    @Schema(description = "원본 방코드") String roomCode, @Schema(description = "썸네일 이미지 URL") String thumbnailUrl,
    @Schema(description = "원본 이미지 URL") String contentUrl, @Schema(description = "생성 시각") LocalDateTime createdAt) {
}
