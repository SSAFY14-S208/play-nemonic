package com.nemonicworld.community.service.moderation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 게시 전 검수 결과입니다.
 *
 * <p>
 * allowed=false이면 커뮤니티 메모를 저장하지 않고, OCR/카테고리 값은 통과한 메모의 감사 정보로만 보관합니다.
 */
public record CommunityMemoModerationResult(boolean allowed, String ocrText, JsonNode categories) {

    public static CommunityMemoModerationResult allowedResult() {
        return new CommunityMemoModerationResult(true, null, null);
    }
}
