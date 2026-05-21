package com.nemonicworld.artifact.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 산출물 공통 썸네일과 종류별 콘텐츠 URL을 반환합니다.
 */
public record ArtifactImageUrlResponse(
    @Schema(description = "산출물 ID", example = "5bc17996-e693-482c-b90d-7dd98c29019e") String artifactId,
    @Schema(description = "산출물 종류", example = "flipbook") String kind,
    @Schema(description = "공통 썸네일 URL") String thumbnailUrl,
    @Schema(description = "산출물 종류별 콘텐츠 URL 목록") List<ArtifactContentUrlResponse> contents) {

    public ArtifactImageUrlResponse {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }
}
