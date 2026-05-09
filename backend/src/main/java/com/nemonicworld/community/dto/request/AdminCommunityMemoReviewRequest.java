package com.nemonicworld.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 커뮤니티 메모 운영 조치 요청입니다.
 */
public record AdminCommunityMemoReviewRequest(
    @Schema(description = "운영자가 입력한 조치 사유. 감사 로그 metadata.reason에 기록됩니다.", example = "신고 내용 확인 결과 부적절한 이미지로 판단했습니다.", requiredMode = Schema.RequiredMode.REQUIRED) String reason) {
}
