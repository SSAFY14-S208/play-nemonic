package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.CommunityDetailResponse;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티 조회 유스케이스를 처리하는 서비스입니다.
 */
@Service
public class CommunityServiceImpl implements CommunityService {

    @Override
    public CommunityDetailResponse getCommunity(Long communityId) {
        return new CommunityDetailResponse(communityId, "샘플 커뮤니티 제목", "공통 API 응답 포맷 예시입니다.");
    }
}
