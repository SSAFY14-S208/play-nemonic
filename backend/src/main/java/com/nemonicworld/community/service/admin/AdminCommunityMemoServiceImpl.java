package com.nemonicworld.community.service.admin;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.dto.request.AdminCommunityMemoReviewRequest;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoReportListResponse;
import com.nemonicworld.community.service.admin.memo.AdminCommunityMemoQueryUseCase;
import com.nemonicworld.community.service.admin.memo.AdminCommunityMemoReviewUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 커뮤니티 메모 검색, 수동 숨김, 복구 정책을 처리합니다.
 */
@Service
public class AdminCommunityMemoServiceImpl implements AdminCommunityMemoService {

    private final AdminCommunityMemoQueryUseCase adminCommunityMemoQueryUseCase;
    private final AdminCommunityMemoReviewUseCase adminCommunityMemoReviewUseCase;

    public AdminCommunityMemoServiceImpl(AdminCommunityMemoQueryUseCase adminCommunityMemoQueryUseCase,
        AdminCommunityMemoReviewUseCase adminCommunityMemoReviewUseCase) {
        this.adminCommunityMemoQueryUseCase = adminCommunityMemoQueryUseCase;
        this.adminCommunityMemoReviewUseCase = adminCommunityMemoReviewUseCase;
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 운영 필터와 페이지 조건으로 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoListResponse getCommunityMemos(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, String pageValue,
        String sizeValue, AdminClientInfo clientInfo) {
        return adminCommunityMemoQueryUseCase.getCommunityMemos(adminPrincipal, hidden, moderationStatus, sourceType,
            reported, keyword, pageValue, sizeValue, clientInfo);
    }

    /**
     * hidden 메모를 포함한 삭제되지 않은 커뮤니티 메모 상세를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoDetailResponse getCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminClientInfo clientInfo) {
        return adminCommunityMemoQueryUseCase.getCommunityMemo(adminPrincipal, memoIdValue, clientInfo);
    }

    /**
     * hidden 메모를 포함한 삭제되지 않은 커뮤니티 메모의 신고 이력을 최신순으로 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoReportListResponse getCommunityMemoReports(AdminPrincipal adminPrincipal,
        String memoIdValue, String reasonValue, String pageValue, String sizeValue, AdminClientInfo clientInfo) {
        return adminCommunityMemoQueryUseCase.getCommunityMemoReports(adminPrincipal, memoIdValue, reasonValue,
            pageValue, sizeValue, clientInfo);
    }

    /**
     * visible 메모를 관리자 수동 숨김 상태로 전환합니다. 이미 hidden이면 상태를 유지하고 상세를 반환합니다.
     */
    @Override
    @Transactional
    public AdminCommunityMemoDetailResponse hideCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        return adminCommunityMemoReviewUseCase.hideCommunityMemo(adminPrincipal, memoIdValue, request, clientInfo);
    }

    /**
     * hidden 메모를 visible 상태로 복구합니다. 이미 visible이면 상태를 유지하고 상세를 반환합니다.
     */
    @Override
    @Transactional
    public AdminCommunityMemoDetailResponse restoreCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        return adminCommunityMemoReviewUseCase.restoreCommunityMemo(adminPrincipal, memoIdValue, request, clientInfo);
    }
}
