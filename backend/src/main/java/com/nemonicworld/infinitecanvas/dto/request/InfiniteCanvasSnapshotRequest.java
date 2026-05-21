package com.nemonicworld.infinitecanvas.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "무한 캔버스 스냅샷 동기화 요청")
public record InfiniteCanvasSnapshotRequest(
    @Schema(description = "클라이언트가 기준으로 삼은 서버 revision", example = "12") Long baseRevision,
    @Schema(description = "현재 캔버스 전체 요소 목록") List<JsonNode> elements,
    @Schema(description = "현재 뷰포트 정보", nullable = true) JsonNode viewport) {
}
