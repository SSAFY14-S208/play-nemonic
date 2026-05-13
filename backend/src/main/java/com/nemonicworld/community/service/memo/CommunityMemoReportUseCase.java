package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.dto.response.CommunityMemoReportResponse;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import com.nemonicworld.community.service.support.CommunityRuntimeSettingsProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 신고와 신고 기준 자동 숨김 유스케이스입니다.
 */
@Service
class CommunityMemoReportUseCase {

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityRuntimeSettingsProvider communityRuntimeSettingsProvider;
    private final CommunityMemoSupport communityMemoSupport;

    CommunityMemoReportUseCase(CommunityMemoRepository communityMemoRepository,
        CommunityRuntimeSettingsProvider communityRuntimeSettingsProvider, CommunityMemoSupport communityMemoSupport) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityRuntimeSettingsProvider = communityRuntimeSettingsProvider;
        this.communityMemoSupport = communityMemoSupport;
    }

    @Transactional
    CommunityMemoReportResponse reportCommunityMemo(String memoIdValue, String userUuidValue,
        CommunityMemoReportRequest request) {
        try {
            return reportCommunityMemoInternal(memoIdValue, userUuidValue, request);
        } catch (RuntimeException e) {
            communityMemoSupport.logCommunityActionFailed("community_memo_report_rejected", memoIdValue, userUuidValue,
                e);
            throw e;
        }
    }

    private CommunityMemoReportResponse reportCommunityMemoInternal(String memoIdValue, String userUuidValue,
        CommunityMemoReportRequest request) {
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);
        CommunityMemoReportReason reason = communityMemoSupport.validateReportReason(request);
        CommunityMemoEventLogger.business("community_memo_report_requested", userUuid,
            metadata("memo_id", memoId, "reason", reason.value(), "reason_detail_present",
                CommunityMemoEventLogger.hasText(request.reasonDetail())));

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            "community_memo_report_rejected");
        if (communityMemoSupport.isOwnedByViewer(row.userId(), userUuid)) {
            CommunityMemoEventLogger.business("community_memo_report_rejected", userUuid,
                metadata("memo_id", memoId, "reason", reason.value(), "reject_reason", "own_memo"));
            throw new BadRequestException(CommunityMemoSupport.OWN_MEMO_REPORT_MESSAGE);
        }

        if (communityMemoRepository.existsMemoReport(memoId, userUuid)) {
            CommunityMemoEventLogger.business("community_memo_report_rejected", userUuid,
                metadata("memo_id", memoId, "reason", reason.value(), "reject_reason", "duplicate"));
            throw new ConflictException(CommunityMemoSupport.DUPLICATE_REPORT_MESSAGE);
        }

        LocalDateTime reportedAt = LocalDateTime.now();
        Long reportId;
        try {
            reportId = communityMemoRepository.insertMemoReport(memoId, userUuid, reason,
                communityMemoSupport.normalizeReasonDetail(request.reasonDetail()), reportedAt);
        } catch (DuplicateKeyException e) {
            CommunityMemoEventLogger.business("community_memo_report_rejected", userUuid,
                metadata("memo_id", memoId, "reason", reason.value(), "reject_reason", "duplicate_key"));
            throw new ConflictException(CommunityMemoSupport.DUPLICATE_REPORT_MESSAGE);
        }

        int reportCount = communityMemoRepository.incrementReportCount(memoId);
        if (reportCount == 0) {
            throw new NotFoundException(CommunityMemoSupport.COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        int reportHideThreshold = communityRuntimeSettingsProvider.currentReportHideThreshold();
        boolean hidden = reportCount >= reportHideThreshold;
        if (hidden) {
            CommunityMemoEventLogger.business("community_memo_report_threshold_reached", userUuid, metadata("memo_id",
                memoId, "report_count", reportCount, "threshold", reportHideThreshold, "reason", reason.value()));
            communityMemoRepository.hideMemoByReportThreshold(memoId, reportedAt, reportHideThreshold);
            CommunityMemoEventLogger.business("community_memo_auto_hidden_by_report", userUuid,
                metadata("memo_id", memoId, "report_count", reportCount, "threshold", reportHideThreshold,
                    "hidden_reason", "report_threshold", "hidden_at", reportedAt));
        }

        String reasonDetail = communityMemoSupport.normalizeReasonDetail(request.reasonDetail());
        CommunityMemoEventLogger.business("community_memo_report_created", userUuid,
            metadata("report_id", reportId, "memo_id", memoId, "memo_owner_uuid", row.userId(), "reason",
                reason.value(), "reason_detail", reasonDetail, "reason_detail_present",
                CommunityMemoEventLogger.hasText(request.reasonDetail()), "report_count", reportCount, "hidden",
                hidden));
        return new CommunityMemoReportResponse(memoId.toString(), reportCount, hidden);
    }
}
