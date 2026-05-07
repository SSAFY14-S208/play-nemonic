package com.nemonicworld.relay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * canvasIndex 하나에 대응하는 릴레이 최종 결과물 정보입니다.
 */
@Schema(description = "릴레이 canvasIndex별 최종 결과")
public record RelayRoomResultItemResponse(@Schema(description = "canvasIndex", example = "0") Integer canvasIndex,
    @Schema(description = "gallery id") String galleryId, @Schema(description = "artifact id") String artifactId,
    @Schema(description = "thumbnail URL") String thumbnailUrl,
    @Schema(description = "combined image URL") String contentUrl,
    @Schema(description = "created at", example = "2026-05-06T13:50:00") LocalDateTime createdAt,
    @Schema(description = "part drawers") List<RelayRoomResultPartResponse> parts) {
}
