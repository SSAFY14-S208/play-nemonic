package com.nemonicworld.flipbook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * flipbookIndex 하나에 대응하는 최종 플립북 결과물 정보입니다.
 */
@Schema(description = "플립북 번호별 최종 결과")
public record FlipbookRoomResultItemResponse(@Schema(description = "플립북 번호", example = "0") Integer flipbookIndex,
    @Schema(description = "갤러리 ID") String galleryId, @Schema(description = "산출물 ID") String artifactId,
    @Schema(description = "썸네일 URL") String thumbnailUrl, @Schema(description = "GIF URL") String gifUrl,
    @Schema(description = "첫 프레임 이미지 URL") String firstImageUrl,
    @Schema(description = "생성 시각", example = "2026-05-08T14:00:00") LocalDateTime createdAt,
    @Schema(description = "프레임 목록") List<FlipbookRoomResultFrameResponse> frames) {

    public FlipbookRoomResultItemResponse {
        frames = frames == null ? List.of() : List.copyOf(frames);
    }
}
