package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.dto.response.CommunityMemoReportResponse;

/**
 * 커뮤니티 메모 조회 유스케이스를 정의합니다.
 */
public interface CommunityMemoService {

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모 목록을 조회합니다.
     */
    CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue);

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모 한 건의 상세를 조회합니다.
     */
    CommunityMemoDetailResponse getCommunityMemo(String memoIdValue, String viewerUserUuidValue);

    /**
     * 최종 렌더링된 원본/썸네일 스냅샷을 검수한 뒤 커뮤니티 벽에 새 메모로 붙입니다.
     */
    CommunityMemoDetailResponse createCommunityMemo(String userUuidValue, CommunityMemoCreateRequest request);

    /**
     * 본인 visible 메모의 위치, 레이어, 회전값만 수정합니다.
     */
    CommunityMemoDetailResponse updateCommunityMemoLayout(String memoIdValue, String userUuidValue,
        CommunityMemoLayoutUpdateRequest request);

    /**
     * 본인 visible 메모를 사용자 삭제 사유로 soft delete 합니다.
     */
    void deleteCommunityMemo(String memoIdValue, String userUuidValue);

    /**
     * visible 메모를 신고하고, 신고 임계값에 도달하면 자동 숨김 처리합니다.
     */
    CommunityMemoReportResponse reportCommunityMemo(String memoIdValue, String userUuidValue,
        CommunityMemoReportRequest request);
}
