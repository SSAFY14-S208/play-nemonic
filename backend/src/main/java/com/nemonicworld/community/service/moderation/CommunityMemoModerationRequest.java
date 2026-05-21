package com.nemonicworld.community.service.moderation;

/**
 * 게시 전 검수 서버로 보낼 최소 입력입니다.
 *
 * <p>
 * imageUrl과 thumbnailUrl은 MinIO 공개 URL이며, clientText는 프론트 텍스트박스 원문 보조 입력입니다.
 */
public record CommunityMemoModerationRequest(String imageUrl, String thumbnailUrl, String clientText,
    String sourceType) {
}
