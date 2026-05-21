package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 위치 수정과 사용자 삭제 유스케이스입니다.
 */
@Service
class CommunityMemoLayoutUseCase {

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoSupport communityMemoSupport;
    private final CommunityMemoResponseMapper communityMemoResponseMapper;

    CommunityMemoLayoutUseCase(CommunityMemoRepository communityMemoRepository,
        CommunityMemoSupport communityMemoSupport, CommunityMemoResponseMapper communityMemoResponseMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoSupport = communityMemoSupport;
        this.communityMemoResponseMapper = communityMemoResponseMapper;
    }

    @Transactional
    CommunityMemoDetailResponse updateCommunityMemoLayout(String memoIdValue, String userUuidValue,
        CommunityMemoLayoutUpdateRequest request) {
        try {
            return updateCommunityMemoLayoutInternal(memoIdValue, userUuidValue, request);
        } catch (RuntimeException e) {
            communityMemoSupport.logCommunityActionFailed("community_memo_layout_update_failed", memoIdValue,
                userUuidValue, e);
            throw e;
        }
    }

    @Transactional
    void deleteCommunityMemo(String memoIdValue, String userUuidValue) {
        try {
            deleteCommunityMemoInternal(memoIdValue, userUuidValue);
        } catch (RuntimeException e) {
            communityMemoSupport.logCommunityActionFailed("community_memo_delete_failed", memoIdValue, userUuidValue,
                e);
            throw e;
        }
    }

    private CommunityMemoDetailResponse updateCommunityMemoLayoutInternal(String memoIdValue, String userUuidValue,
        CommunityMemoLayoutUpdateRequest request) {
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);
        communityMemoSupport.validateLayout(request);
        CommunityMemoEventLogger.business("community_memo_layout_update_requested", userUuid,
            metadata("memo_id", memoId, "position_x", request.positionX(), "position_y", request.positionY(), "z_index",
                request.zIndex(), "rotation_deg", request.rotationDeg()));

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            "community_memo_layout_update_failed");
        if (!communityMemoSupport.isOwnedByViewer(row.userId(), userUuid)) {
            CommunityMemoEventLogger.business("community_memo_layout_update_denied", userUuid,
                metadata("memo_id", memoId, "memo_owner_uuid", row.userId(), "reason", "not_owner"));
            throw new ForbiddenException(CommunityMemoSupport.MEMO_ACCESS_DENIED_MESSAGE);
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedCount = communityMemoRepository.updateMemoLayout(memoId, userUuid, request.positionX(),
            request.positionY(), request.zIndex(), request.rotationDeg().floatValue(), updatedAt);
        if (updatedCount == 0) {
            throw new NotFoundException(CommunityMemoSupport.COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        CommunityMemoDetailRow updatedRow = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(CommunityMemoSupport.COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        CommunityMemoEventLogger.business("community_memo_layout_updated", userUuid,
            metadata("memo_id", memoId, "source_type", CommunityMemoEventLogger.sourceType(updatedRow.artifactId()),
                "before_position_x", row.positionX(), "before_position_y", row.positionY(), "before_z_index",
                row.zIndex(), "before_rotation_deg", row.rotationDeg(), "after_position_x", updatedRow.positionX(),
                "after_position_y", updatedRow.positionY(), "after_z_index", updatedRow.zIndex(), "after_rotation_deg",
                updatedRow.rotationDeg()));
        return communityMemoResponseMapper.toDetailResponse(updatedRow, userUuid);
    }

    private void deleteCommunityMemoInternal(String memoIdValue, String userUuidValue) {
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);
        CommunityMemoEventLogger.business("community_memo_delete_requested", userUuid, metadata("memo_id", memoId));

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            "community_memo_delete_failed");
        if (!communityMemoSupport.isOwnedByViewer(row.userId(), userUuid)) {
            CommunityMemoEventLogger.business("community_memo_delete_denied", userUuid,
                metadata("memo_id", memoId, "memo_owner_uuid", row.userId(), "reason", "not_owner"));
            throw new ForbiddenException(CommunityMemoSupport.MEMO_DELETE_ACCESS_DENIED_MESSAGE);
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        int deletedCount = communityMemoRepository.softDeleteMemo(memoId, userUuid, deletedAt);
        if (deletedCount == 0) {
            throw new NotFoundException(CommunityMemoSupport.COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }
        CommunityMemoEventLogger.business("community_memo_user_deleted", userUuid,
            metadata("memo_id", memoId, "source_type", CommunityMemoEventLogger.sourceType(row.artifactId()),
                "deleted_reason", "user_delete", "deleted_at", deletedAt));
    }
}
