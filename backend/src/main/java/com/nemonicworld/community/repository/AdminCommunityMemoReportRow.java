package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 신고 내역 조회에 필요한 DB row projection입니다.
 */
public record AdminCommunityMemoReportRow(Long reportId, UUID memoId, UUID reporterUserId, String reporterNickname,
    String reason, String reasonDetail, LocalDateTime createdAt) {
}
