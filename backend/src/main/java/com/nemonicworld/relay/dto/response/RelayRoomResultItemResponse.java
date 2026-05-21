package com.nemonicworld.relay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * canvasIndex 하나에 대응하는 릴레이 최종 결과물 정보입니다.
 */
@Schema(description = "릴레이 캔버스 번호별 최종 결과")
public record RelayRoomResultItemResponse(@Schema(description = "캔버스 번호", example = "0") Integer canvasIndex,
    @Schema(description = "갤러리 ID") String galleryId, @Schema(description = "산출물 ID") String artifactId,
    @Schema(description = "썸네일 URL") String thumbnailUrl, @Schema(description = "합성 이미지 URL") String contentUrl,
    @Schema(description = "생성 시각", example = "2026-05-06T13:50:00") LocalDateTime createdAt,
    @Schema(description = "파트별 작성자 목록") List<RelayRoomResultPartResponse> parts) {
}
