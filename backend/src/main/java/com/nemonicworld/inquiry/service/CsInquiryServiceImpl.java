package com.nemonicworld.inquiry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.EmailDeliveryException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.request.CsInquiryReplyRequest;
import com.nemonicworld.inquiry.dto.request.CsInquiryStatusUpdateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryDetailResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListItemResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryReplyResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryStatusUpdateResponse;
import com.nemonicworld.inquiry.entity.CsInquiry;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.entity.CsInquiryType;
import com.nemonicworld.inquiry.repository.CsInquiryInsertCommand;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class CsInquiryServiceImpl implements CsInquiryService {

    private static final String INVALID_JSON_MESSAGE = "문의 데이터 형식이 올바르지 않습니다.";
    private static final String REQUIRED_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL을 입력해 주세요.";
    private static final String INVALID_ATTACHMENT_URL_MESSAGE = "첨부 파일 URL 형식이 올바르지 않습니다.";
    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String INVALID_USER_UUID_MESSAGE = "사용자 UUID 형식이 올바르지 않습니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String INVALID_INQUIRY_ID_MESSAGE = "문의 ID가 올바르지 않습니다.";
    private static final String INQUIRY_NOT_FOUND_MESSAGE = "고객 문의를 찾을 수 없습니다.";
    private static final String REQUIRED_EMAIL_MESSAGE = "이메일이 없어 회신할 수 없습니다.";
    private static final String CLOSED_INQUIRY_REPLY_MESSAGE = "종료된 문의에는 회신할 수 없습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final TypeReference<List<String>> ATTACHMENTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {
    };

    private final AnonymousUserResolver anonymousUserResolver;
    private final CsInquiryRepository csInquiryRepository;
    private final InquiryMailSender inquiryMailSender;
    private final ObjectMapper objectMapper;
    private final AdminAuditLogger adminAuditLogger;

    public CsInquiryServiceImpl(AnonymousUserResolver anonymousUserResolver, CsInquiryRepository csInquiryRepository,
        InquiryMailSender inquiryMailSender, ObjectMapper objectMapper, AdminAuditLogger adminAuditLogger) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.csInquiryRepository = csInquiryRepository;
        this.inquiryMailSender = inquiryMailSender;
        this.objectMapper = objectMapper;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    @Transactional
    public CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String type = CsInquiryType.fromValue(request.type()).getValue();
        CsInquiryInsertCommand command = new CsInquiryInsertCommand(user.getId(), type,
            normalizeRequiredTrimmed(request.title()), normalizeOptional(request.content()),
            normalizeOptional(request.email()), serializeAttachments(request.attachments()),
            serializeMeta(request.meta(), userAgent, referer, now), CsInquiryStatus.NEW.getValue(), now, now);

        CsInquiryCreateResponse response = CsInquiryCreateResponse.from(csInquiryRepository.insertInquiry(command));
        StructuredEventLogger.apiBusiness("inquiry_created", "inquiry", user.getId().toString(),
            StructuredEventLogger.metadata("inquiry_id", response.id(), "type", type, "has_email",
                StringUtils.hasText(request.email()), "attachment_count",
                request.attachments() == null ? 0 : request.attachments().size(), "result", "success"));

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String pageValue, String sizeValue) {
        requireAdmin(adminPrincipal);

        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedType = normalizeOptionalType(type);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        UUID normalizedUserUuid = parseOptionalUserUuid(userUuid);

        long totalElements = csInquiryRepository.countInquiries(normalizedStatus, normalizedType, normalizedKeyword,
            normalizedUserUuid);
        List<CsInquiryListItemResponse> items = csInquiryRepository.findInquiries(normalizedStatus, normalizedType,
            normalizedKeyword, normalizedUserUuid, size, calculateOffset(page, size)).stream()
            .map(CsInquiryListItemResponse::from).toList();

        return new CsInquiryListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    @Override
    @Transactional(readOnly = true)
    public CsInquiryDetailResponse getInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue) {
        requireAdmin(adminPrincipal);

        Long inquiryId = parseInquiryId(inquiryIdValue);

        return csInquiryRepository
            .findById(inquiryId).map(inquiry -> CsInquiryDetailResponse.of(inquiry,
                parseAttachments(inquiry.getAttachments()), parseMeta(inquiry.getMeta())))
            .orElseThrow(() -> new NotFoundException(INQUIRY_NOT_FOUND_MESSAGE));
    }

    @Override
    @Transactional
    public CsInquiryReplyResponse replyInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryReplyRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

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

    @Override
    @Transactional
    public CsInquiryStatusUpdateResponse updateInquiryStatus(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryStatusUpdateRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

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

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
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

    private String normalizeRequiredTrimmed(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String normalizeOptionalKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedStatus = value.trim().toLowerCase(Locale.ROOT);

        return CsInquiryStatus.fromValue(normalizedStatus).getValue();
    }

    private String normalizeOptionalType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedType = value.trim().toLowerCase(Locale.ROOT);

        return CsInquiryType.fromValue(normalizedType).getValue();
    }

    private UUID parseOptionalUserUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_USER_UUID_MESSAGE);
        }
    }

    private int parsePage(String pageValue) {
        int page = parseIntegerOrDefault(pageValue, DEFAULT_PAGE);
        if (page < 0) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return page;
    }

    private int parseSize(String sizeValue) {
        int size = parseIntegerOrDefault(sizeValue, DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return size;
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }
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

    private List<String> parseAttachments(String attachments) {
        if (!StringUtils.hasText(attachments)) {
            return List.of();
        }

        try {
            List<String> parsedAttachments = objectMapper.readValue(attachments, ATTACHMENTS_TYPE);

            return parsedAttachments == null ? List.of() : parsedAttachments;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMeta(String meta) {
        if (!StringUtils.hasText(meta)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedMeta = objectMapper.readValue(meta, META_TYPE);

            return parsedMeta == null ? Map.of() : parsedMeta;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }

    private String serializeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        validateAttachmentUrls(attachments);

        return writeJson(attachments);
    }

    private void validateAttachmentUrls(List<String> attachments) {
        for (String attachment : attachments) {
            if (!StringUtils.hasText(attachment)) {
                throw new BadRequestException(REQUIRED_ATTACHMENT_URL_MESSAGE);
            }

            if (!isHttpUrl(attachment)) {
                throw new BadRequestException(INVALID_ATTACHMENT_URL_MESSAGE);
            }
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);

            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String serializeMeta(Map<String, Object> requestMeta, String userAgent, String referer,
        LocalDateTime createdAt) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (requestMeta != null) {
            meta.putAll(requestMeta);
        }
        if (StringUtils.hasText(userAgent)) {
            meta.put("userAgent", userAgent);
        }
        if (StringUtils.hasText(referer)) {
            meta.put("referer", referer);
        }
        meta.put("createdAt", createdAt.toString());

        return writeJson(meta);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_JSON_MESSAGE);
        }
    }
}
