package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 관리자 커뮤니티 메모 신고 내역의 단일 항목 응답입니다.
 */
public record AdminCommunityMemoReportItemResponse(@Schema(description = "커뮤니티 메모 신고 ID", example = "12") Long reportId,
    @Schema(description = "신고된 커뮤니티 메모 UUID", example = "3f22df4f-b187-42dd-81f3-d9f2fa5ce001") String memoId,
    @Schema(description = "신고자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String reporterUserUuid,
    @Schema(description = "신고자 닉네임", example = "망고") String reporterNickname,
    @Schema(description = "신고 사유 enum 값", example = "inappropriate") String reason,
    @Schema(description = "신고 상세 사유", nullable = true, example = "욕설이 포함되어 있어요.") String reasonDetail,
    @Schema(description = "신고 접수 시각", example = "2026-05-10T14:30:00") LocalDateTime createdAt) {
}
