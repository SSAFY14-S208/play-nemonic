package com.nemonicworld.artifact.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 산출물 종류별 실제 콘텐츠 파일 URL입니다.
 */
public record ArtifactContentUrlResponse(@Schema(description = "콘텐츠 타입", example = "combined_preview") String type,
    @Schema(description = "브라우저에서 접근 가능한 콘텐츠 URL") String url) {
}
