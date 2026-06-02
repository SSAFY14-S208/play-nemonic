package com.nemonicworld.community.service.admin.memo;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.dto.request.AdminCommunityMemoReviewRequest;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.repository.AdminCommunityMemoRepository;
import com.nemonicworld.community.repository.AdminCommunityMemoRow;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class AdminCommunityMemoReviewUseCase {

    private static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    private static final String INVALID_HIDE_REASON_MESSAGE = "커뮤니티 메모 숨김 사유가 올바르지 않습니다.";
    private static final String INVALID_RESTORE_REASON_MESSAGE = "커뮤니티 메모 복구 사유가 올바르지 않습니다.";

    private final AdminCommunityMemoRepository adminCommunityMemoRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final AdminCommunityMemoQueryUseCase adminCommunityMemoQueryUseCase;

    public AdminCommunityMemoReviewUseCase(AdminCommunityMemoRepository adminCommunityMemoRepository,
        AdminAuditLogger adminAuditLogger, AdminCommunityMemoQueryUseCase adminCommunityMemoQueryUseCase) {
        this.adminCommunityMemoRepository = adminCommunityMemoRepository;
        this.adminAuditLogger = adminAuditLogger;
        this.adminCommunityMemoQueryUseCase = adminCommunityMemoQueryUseCase;
    }

    @Transactional
    public AdminCommunityMemoDetailResponse hideCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        UUID memoId = adminCommunityMemoQueryUseCase.parseMemoId(memoIdValue);
        String reason = validateReviewReason(request, INVALID_HIDE_REASON_MESSAGE);
        adminAuditLogger.logCommunityMemoHideRequested(adminPrincipal, memoId.toString(), reason, clientInfo);

        AdminCommunityMemoRow row = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (row.hidden()) {
            emitAfterCommit(() -> {
                adminAuditLogger.logMemoSoftDelete(adminPrincipal, memoId.toString(), reason, clientInfo, false);
                adminAuditLogger.logCommunityMemoHidden(adminPrincipal, memoId.toString(), reason, clientInfo, false,
                    hiddenStateMetadata(row, row));
            });
            return adminCommunityMemoQueryUseCase.toDetailResponse(row);
        }

        LocalDateTime hiddenAt = LocalDateTime.now();
        int updatedCount = adminCommunityMemoRepository.hideMemo(memoId, adminPrincipal.id(), hiddenAt);
        if (updatedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        AdminCommunityMemoRow updatedRow = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        AdminCommunityMemoDetailResponse response = adminCommunityMemoQueryUseCase.toDetailResponse(updatedRow);
        emitAfterCommit(() -> {
            adminAuditLogger.logMemoSoftDelete(adminPrincipal, memoId.toString(), reason, clientInfo, true);
            adminAuditLogger.logCommunityMemoHidden(adminPrincipal, memoId.toString(), reason, clientInfo, true,
                hiddenStateMetadata(row, updatedRow));
        });

        return response;
    }

    @Transactional
    public AdminCommunityMemoDetailResponse restoreCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        UUID memoId = adminCommunityMemoQueryUseCase.parseMemoId(memoIdValue);
        String reason = validateReviewReason(request, INVALID_RESTORE_REASON_MESSAGE);
        adminAuditLogger.logCommunityMemoRestoreRequested(adminPrincipal, memoId.toString(), reason, clientInfo);

        AdminCommunityMemoRow row = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (!row.hidden()) {
            emitAfterCommit(() -> {
                adminAuditLogger.logMemoRestore(adminPrincipal, memoId.toString(), reason, clientInfo, false, null);
                adminAuditLogger.logCommunityMemoRestored(adminPrincipal, memoId.toString(), reason, clientInfo, false,
                    hiddenStateMetadata(row, row));
            });
            return adminCommunityMemoQueryUseCase.toDetailResponse(row);
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedCount = adminCommunityMemoRepository.restoreMemo(memoId, adminPrincipal.id(), updatedAt);
        if (updatedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        AdminCommunityMemoRow updatedRow = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        AdminCommunityMemoDetailResponse response = adminCommunityMemoQueryUseCase.toDetailResponse(updatedRow);
        emitAfterCommit(() -> {
            adminAuditLogger.logMemoRestore(adminPrincipal, memoId.toString(), reason, clientInfo, true,
                row.hiddenReason());
            adminAuditLogger.logCommunityMemoRestored(adminPrincipal, memoId.toString(), reason, clientInfo, true,
                hiddenStateMetadata(row, updatedRow));
        });

        return response;
    }

    private Map<String, Object> hiddenStateMetadata(AdminCommunityMemoRow before, AdminCommunityMemoRow after) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("before_is_hidden", before.hidden());
        metadata.put("after_is_hidden", after.hidden());
        metadata.put("before_hidden_reason", before.hiddenReason());
        metadata.put("after_hidden_reason", after.hiddenReason());
        metadata.put("before_hidden_at", before.hiddenAt());
        metadata.put("after_hidden_at", after.hiddenAt());
        metadata.put("before_reviewed_by", before.reviewedBy());
        metadata.put("after_reviewed_by", after.reviewedBy());
        metadata.put("before_reviewed_at", before.reviewedAt());
        metadata.put("after_reviewed_at", after.reviewedAt());
        metadata.put("before_report_count", before.reportCount());
        metadata.put("after_report_count", after.reportCount());
        return metadata;
    }

    private void emitAfterCommit(Runnable auditLog) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditLog.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditLog.run();
            }
        });
    }

    private String validateReviewReason(AdminCommunityMemoReviewRequest request, String message) {
        String reason = request == null ? null : request.reason();
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(message);
        }

        return reason.trim();
    }
}
