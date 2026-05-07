package com.nemonicworld.community.service.moderation;

public record CommunityMemoModerationRequest(String imageUrl, String thumbnailUrl, String clientText,
    String sourceType) {
}
