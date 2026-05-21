package com.nemonicworld.infinitecanvas.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 단일 편집 연산")
public record InfiniteCanvasOperationRequest(
    @Schema(description = "서버 확정 전 클라이언트가 만든 연산 ID", nullable = true) String operationId,
    @Schema(description = "멱등 처리를 위한 클라이언트 연산 ID") String clientOperationId,
    @Schema(description = "연산 타입", example = "UPSERT_ELEMENT") InfiniteCanvasOperationType operationType,
    @Schema(description = "대상 요소 ID", nullable = true) String elementId,
    @Schema(description = "생성/수정할 요소 JSON", nullable = true) JsonNode element,
    @Schema(description = "연산 보조 payload", nullable = true) JsonNode payload) {
}
