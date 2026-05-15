package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "무한 캔버스 편집 연산 묶음 요청")
public record InfiniteCanvasOpsRequest(
    @Schema(description = "클라이언트가 기준으로 삼은 서버 revision", example = "12") Long baseRevision,
    @Schema(description = "순서대로 적용할 편집 연산 목록") List<InfiniteCanvasOperationRequest> operations) {
}
