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
        return new CommunityDetailResponse(communityId, "\uc0d8\ud50c \ucee4\ubba4\ub2c8\ud2f0 \uc81c\ubaa9",
            "\uacf5\ud1b5 API \uc751\ub2f5 \ud3ec\ub9f7 \uc608\uc2dc\uc785\ub2c8\ub2e4.");
    }
}
