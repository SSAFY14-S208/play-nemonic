package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.response.CommunityMemoListResponse;

/**
 * 커뮤니티 메모 조회 유스케이스를 정의합니다.
 */
public interface CommunityMemoService {

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모 목록을 조회합니다.
     */
    CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue);
}
