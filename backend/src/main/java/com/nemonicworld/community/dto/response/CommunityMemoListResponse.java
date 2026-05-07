package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 커뮤니티 캔버스 공용 벽 메모 목록 응답입니다.
 */
public record CommunityMemoListResponse(@Schema(description = "커뮤니티 메모 목록") List<CommunityMemoItemResponse> items,
    @Schema(description = "조회된 전체 메모 수", example = "1") long totalElements) {
}
