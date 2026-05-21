package com.nemonicworld.community.service.moderation;

public interface CommunityMemoModerationClient {

    /**
     * 커뮤니티 메모 게시 전 이미지/텍스트 검수를 수행합니다.
     */
    CommunityMemoModerationResult check(CommunityMemoModerationRequest request);
}
