package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커뮤니티 메모 신고 결과 응답입니다.
 */
@Schema(description = "커뮤니티 메모 신고 응답")
public record CommunityMemoReportResponse(
    @Schema(description = "신고된 커뮤니티 메모 UUID", example = "3f22df4f-b187-42dd-81f3-d9f2fa5ce001") String memoId,
    @Schema(description = "누적 신고 횟수", example = "5") int reportCount,
    @Schema(description = "이번 신고로 자동 숨김 처리되었거나 숨김 기준에 도달했는지 여부", example = "true") boolean hidden) {
}
