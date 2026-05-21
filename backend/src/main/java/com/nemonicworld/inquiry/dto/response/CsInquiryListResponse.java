package com.nemonicworld.inquiry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 고객 문의 목록 조회 응답")
public record CsInquiryListResponse(@Schema(description = "문의 목록") List<CsInquiryListItemResponse> items,
    @Schema(description = "현재 페이지 번호", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 문의 수", example = "1") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {
}
