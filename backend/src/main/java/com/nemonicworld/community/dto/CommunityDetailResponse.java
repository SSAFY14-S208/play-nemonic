package com.nemonicworld.community.dto;

public record CommunityDetailResponse(
        Long communityId,
        String title,
        String content
) {
}
