package com.nemonicworld.community.service.memo;

import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.dto.response.CommunityMemoReportResponse;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티 메모 컨트롤러가 사용하는 facade입니다.
 */
@Service
public class CommunityMemoServiceImpl implements CommunityMemoService {

    private final CommunityMemoQueryUseCase communityMemoQueryUseCase;
    private final CommunityMemoCreateUseCase communityMemoCreateUseCase;
    private final CommunityMemoLayoutUseCase communityMemoLayoutUseCase;
    private final CommunityMemoReportUseCase communityMemoReportUseCase;

    public CommunityMemoServiceImpl(CommunityMemoQueryUseCase communityMemoQueryUseCase,
        CommunityMemoCreateUseCase communityMemoCreateUseCase, CommunityMemoLayoutUseCase communityMemoLayoutUseCase,
        CommunityMemoReportUseCase communityMemoReportUseCase) {
        this.communityMemoQueryUseCase = communityMemoQueryUseCase;
        this.communityMemoCreateUseCase = communityMemoCreateUseCase;
        this.communityMemoLayoutUseCase = communityMemoLayoutUseCase;
        this.communityMemoReportUseCase = communityMemoReportUseCase;
    }

    @Override
    public CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue) {
        return communityMemoQueryUseCase.getCommunityMemos(viewerUserUuidValue);
    }

    @Override
    public CommunityMemoDetailResponse getCommunityMemo(String memoIdValue, String viewerUserUuidValue) {
        return communityMemoQueryUseCase.getCommunityMemo(memoIdValue, viewerUserUuidValue);
    }

    @Override
    public CommunityMemoDetailResponse createCommunityMemo(String userUuidValue, CommunityMemoCreateRequest request) {
        return communityMemoCreateUseCase.createCommunityMemo(userUuidValue, request);
    }

    @Override
    public CommunityMemoDetailResponse updateCommunityMemoLayout(String memoIdValue, String userUuidValue,
        CommunityMemoLayoutUpdateRequest request) {
        return communityMemoLayoutUseCase.updateCommunityMemoLayout(memoIdValue, userUuidValue, request);
    }

    @Override
    public void deleteCommunityMemo(String memoIdValue, String userUuidValue) {
        communityMemoLayoutUseCase.deleteCommunityMemo(memoIdValue, userUuidValue);
    }

    @Override
    public CommunityMemoReportResponse reportCommunityMemo(String memoIdValue, String userUuidValue,
        CommunityMemoReportRequest request) {
        return communityMemoReportUseCase.reportCommunityMemo(memoIdValue, userUuidValue, request);
    }
}
