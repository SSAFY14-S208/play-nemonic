package com.nemonicworld.inquiry.service.admin;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.EmailDeliveryException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.inquiry.dto.request.CsInquiryReplyRequest;
import com.nemonicworld.inquiry.dto.request.CsInquiryStatusUpdateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryReplyResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryStatusUpdateResponse;
import com.nemonicworld.inquiry.entity.CsInquiry;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.inquiry.service.InquiryMailSender;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class AdminInquiryCommandUseCase {

    private static final String INVALID_INQUIRY_ID_MESSAGE = "문의 ID가 올바르지 않습니다.";
    private static final String INQUIRY_NOT_FOUND_MESSAGE = "고객 문의를 찾을 수 없습니다.";
    private static final String REQUIRED_EMAIL_MESSAGE = "이메일이 없어 회신할 수 없습니다.";
    private static final String CLOSED_INQUIRY_REPLY_MESSAGE = "종료된 문의에는 회신할 수 없습니다.";

    private final CsInquiryRepository csInquiryRepository;
    private final InquiryMailSender inquiryMailSender;
    private final AdminAuditLogger adminAuditLogger;

    public AdminInquiryCommandUseCase(CsInquiryRepository csInquiryRepository, InquiryMailSender inquiryMailSender,
        AdminAuditLogger adminAuditLogger) {
        this.csInquiryRepository = csInquiryRepository;
        this.inquiryMailSender = inquiryMailSender;
        this.adminAuditLogger = adminAuditLogger;
    }

    public CsInquiryReplyResponse replyInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryReplyRequest request, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

        Long inquiryId = parseInquiryId(inquiryIdValue);
        CsInquiry inquiry = csInquiryRepository.findById(inquiryId)
            .orElseThrow(() -> new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE));
        if (!StringUtils.hasText(inquiry.getEmail())) {
            throw new BadRequestException(REQUIRED_EMAIL_MESSAGE);
        }
        if (CsInquiryStatus.CLOSED.getValue().equals(inquiry.getStatus())) {
            throw new ConflictException(CLOSED_INQUIRY_REPLY_MESSAGE);
        }

        String subject = normalizeRequiredTrimmed(request.subject());
        String message = normalizeRequiredTrimmed(request.message());
        try {
            inquiryMailSender.sendReply(inquiry.getEmail(), subject, message);
        } catch (EmailDeliveryException e) {
            StructuredEventLogger.apiBusinessWarn("inquiry_reply_email_failed", "inquiry",
                inquiry.getUserId().toString(), "inquiry reply email failed",
                StructuredEventLogger.metadata("inquiry_id", inquiryId, "admin_id", adminPrincipal.id(), "result",
                    "failed", "reason_code", e.getClass().getSimpleName()),
                e);
            throw e;
        }

        LocalDateTime respondedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int updatedCount = csInquiryRepository.updateReply(inquiryId, adminPrincipal.id(), message, respondedAt,
            CsInquiryStatus.RESOLVED.getValue());
        if (updatedCount == 0) {
            throw new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE);
        }

        emitAfterCommit(() -> adminAuditLogger.logInquiryReplySend(adminPrincipal, inquiryId.toString(),
            inquiry.getStatus(), CsInquiryStatus.RESOLVED.getValue(), clientInfo));

        return CsInquiryReplyResponse.from(csInquiryRepository.findById(inquiryId).orElseThrow());
    }

    @Transactional
    public CsInquiryStatusUpdateResponse updateInquiryStatus(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryStatusUpdateRequest request, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

        Long inquiryId = parseInquiryId(inquiryIdValue);
        String status = CsInquiryStatus.fromValue(request.status().trim().toLowerCase(Locale.ROOT)).getValue();
        CsInquiry inquiry = csInquiryRepository.findById(inquiryId)
            .orElseThrow(() -> new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE));
        LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int updatedCount = csInquiryRepository.updateStatus(inquiryId, status, updatedAt);
        if (updatedCount == 0) {
            throw new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE);
        }

        emitAfterCommit(() -> adminAuditLogger.logInquiryStatusChange(adminPrincipal, inquiryId.toString(),
            inquiry.getStatus(), status, clientInfo));

        return CsInquiryStatusUpdateResponse.from(csInquiryRepository.findById(inquiryId).orElseThrow());
    }

    private String normalizeRequiredTrimmed(String value) {
        return value.trim();
    }

    private Long parseInquiryId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
        }

        try {
            long inquiryId = Long.parseLong(value);
            if (inquiryId <= 0) {
                throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
            }

            return inquiryId;
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_INQUIRY_ID_MESSAGE);
        }
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
}
