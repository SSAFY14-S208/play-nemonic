package com.nemonicworld.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 커뮤니티 메모 숨김 처리 요청입니다.
 */
public record AdminCommunityMemoHideRequest(
    @Schema(description = "관리자 수동 숨김 사유. 생략하면 admin_hidden으로 처리합니다.", example = "admin_hidden") String reason) {
}
