package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.CommunityDetailResponse;

/**
 * 커뮤니티 조회 유스케이스를 정의합니다.
 */
public interface CommunityService {

    CommunityDetailResponse getCommunity(Long communityId);
}
