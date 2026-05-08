package com.nemonicworld.phone.dto.response;

import com.nemonicworld.phone.entity.PhoneDrawingArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "휴대폰 그림 갤러리 저장 응답")
public record PhoneDrawingSaveResponse(@Schema(description = "갤러리 항목 ID") String galleryId,
    @Schema(description = "산출물 ID") String artifactId, @Schema(description = "산출물 종류", example = "phone") String kind,
    @Schema(description = "썸네일 이미지 URL") String thumbnailUrl, @Schema(description = "원본 이미지 URL") String contentUrl,
    @Schema(description = "생성 시각") LocalDateTime createdAt) {

    public static PhoneDrawingSaveResponse from(PhoneDrawingArtifact artifact, String thumbnailUrl, String contentUrl) {
        return new PhoneDrawingSaveResponse(artifact.galleryId().toString(), artifact.artifactId().toString(),
            artifact.kind(), thumbnailUrl, contentUrl, artifact.createdAt());
    }
}
