package com.nemonicworld.backoffice.infinitecanvas.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "백오피스 활성 무한 캔버스 목록 응답")
public record BackofficeInfiniteCanvasListResponse(
    @Schema(description = "현재 페이지 항목 목록") List<BackofficeInfiniteCanvasResponse> items,

    @Schema(description = "필터 적용 후 전체 항목 수", example = "17") long totalElements,

    @Schema(description = "현재 페이지 번호 (0-based)", example = "0") int page,

    @Schema(description = "페이지 크기", example = "20") int size) {

    public BackofficeInfiniteCanvasListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
