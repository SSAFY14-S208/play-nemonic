package com.nemonicworld.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커뮤니티 메모 신고 요청입니다.
 */
@Schema(description = "커뮤니티 메모 신고 요청")
public record CommunityMemoReportRequest(@Schema(description = "신고 사유", example = "inappropriate") String reason) {
}
