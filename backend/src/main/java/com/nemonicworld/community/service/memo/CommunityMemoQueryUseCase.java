package com.nemonicworld.community.service.memo;

import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 목록/상세 조회 유스케이스입니다.
 */
@Service
class CommunityMemoQueryUseCase {

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoSupport communityMemoSupport;
    private final CommunityMemoResponseMapper communityMemoResponseMapper;

    CommunityMemoQueryUseCase(CommunityMemoRepository communityMemoRepository,
        CommunityMemoSupport communityMemoSupport, CommunityMemoResponseMapper communityMemoResponseMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoSupport = communityMemoSupport;
        this.communityMemoResponseMapper = communityMemoResponseMapper;
    }

    @Transactional(readOnly = true)
    CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue) {
        long startedAt = System.nanoTime();
        UUID viewerUserUuid = communityMemoSupport.parseOptionalViewerUuid(viewerUserUuidValue);
        List<CommunityMemoItemResponse> items = communityMemoRepository.findVisibleMemos().stream()
            .map(row -> communityMemoResponseMapper.toResponse(row, viewerUserUuid)).toList();

        CommunityMemoEventLogger.business("community_memo_list_viewed", viewerUserUuid,
            metadata("viewer_user_uuid_present", viewerUserUuid != null, "item_count", items.size(), "duration_ms",
                communityMemoSupport.calculateLatencyMs(startedAt), "status", "success"));
        return new CommunityMemoListResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    CommunityMemoDetailResponse getCommunityMemo(String memoIdValue, String viewerUserUuidValue) {
        long startedAt = System.nanoTime();
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID viewerUserUuid = communityMemoSupport.parseOptionalViewerUuid(viewerUserUuidValue);
        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, viewerUserUuid,
            "community_memo_detail_not_found", startedAt);

        CommunityMemoEventLogger.business("community_memo_detail_viewed", viewerUserUuid,
            metadata("memo_id", memoId, "viewer_user_uuid_present", viewerUserUuid != null, "owned_by_me",
                communityMemoSupport.isOwnedByViewer(row.userId(), viewerUserUuid), "source_type",
                CommunityMemoEventLogger.sourceType(row.artifactId()), "report_count", row.reportCount(), "duration_ms",
                communityMemoSupport.calculateLatencyMs(startedAt), "status", "success"));
        return communityMemoResponseMapper.toDetailResponse(row, viewerUserUuid);
    }
}
