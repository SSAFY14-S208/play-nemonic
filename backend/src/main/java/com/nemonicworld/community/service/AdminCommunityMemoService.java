package com.nemonicworld.community.service;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;

/**
 * 관리자 커뮤니티 메모 검토 API의 공개 유스케이스입니다.
 */
public interface AdminCommunityMemoService {

    AdminCommunityMemoListResponse getCommunityMemos(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, String keyword, String page, String size);

    AdminCommunityMemoDetailResponse getCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue);

    AdminCommunityMemoDetailResponse hideCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue);

    AdminCommunityMemoDetailResponse restoreCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue);
}
