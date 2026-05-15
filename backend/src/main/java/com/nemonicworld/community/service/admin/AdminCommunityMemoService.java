package com.nemonicworld.community.service.admin;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.dto.request.AdminCommunityMemoReviewRequest;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoReportListResponse;

/**
 * 관리자 커뮤니티 메모 검토 API의 공개 유스케이스입니다.
 */
public interface AdminCommunityMemoService {

    AdminCommunityMemoListResponse getCommunityMemos(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, String page, String size,
        AdminClientInfo clientInfo);

    AdminCommunityMemoDetailResponse getCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminClientInfo clientInfo);

    AdminCommunityMemoReportListResponse getCommunityMemoReports(AdminPrincipal adminPrincipal, String memoIdValue,
        String reason, String page, String size, AdminClientInfo clientInfo);

    AdminCommunityMemoDetailResponse hideCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo);

    AdminCommunityMemoDetailResponse restoreCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo);
}
