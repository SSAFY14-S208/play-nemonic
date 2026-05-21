package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 관리자 커뮤니티 메모 신고 내역 페이징 응답입니다.
 */
public record AdminCommunityMemoReportListResponse(
    @Schema(description = "관리자 커뮤니티 메모 신고 내역") List<AdminCommunityMemoReportItemResponse> items,
    @Schema(description = "현재 페이지 번호", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 신고 수", example = "1") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {
}
