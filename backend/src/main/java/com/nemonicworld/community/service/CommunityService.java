package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.CommunityDetailResponse;

/**
 * 커뮤니티 조회 유스케이스를 정의합니다.
 */
public interface CommunityService {

    /**
     * 커뮤니티 ID에 해당하는 상세 정보를 조회합니다.
     */
    CommunityDetailResponse getCommunity(Long communityId);
}
