package com.nemonicworld.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.community.dto.request.AdminCommunityMemoReviewRequest;
import com.nemonicworld.community.dto.response.AdminCommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoListResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoReportItemResponse;
import com.nemonicworld.community.dto.response.AdminCommunityMemoReportListResponse;
import com.nemonicworld.community.entity.CommunityMemoModerationStatus;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.AdminCommunityMemoRepository;
import com.nemonicworld.community.repository.AdminCommunityMemoReportRow;
import com.nemonicworld.community.repository.AdminCommunityMemoRow;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 관리자 커뮤니티 메모 검토, 수동 숨김, 복구 정책을 처리합니다.
 */
@Service
public class AdminCommunityMemoServiceImpl implements AdminCommunityMemoService {

    private static final Logger log = LoggerFactory.getLogger(AdminCommunityMemoServiceImpl.class);
    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    private static final String INVALID_QUERY_MESSAGE = "관리자 커뮤니티 메모 조회 조건이 올바르지 않습니다.";
    private static final String INVALID_HIDE_REASON_MESSAGE = "커뮤니티 메모 숨김 사유가 올바르지 않습니다.";
    private static final String INVALID_RESTORE_REASON_MESSAGE = "커뮤니티 메모 복구 사유가 올바르지 않습니다.";
    private static final String INVALID_REPORT_REASON_MESSAGE = "커뮤니티 메모 신고 사유가 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final TypeReference<Map<String, Object>> DECORATION_TYPE = new TypeReference<>() {
    };

    private final AdminCommunityMemoRepository adminCommunityMemoRepository;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ObjectMapper objectMapper;
    private final AdminAuditLogger adminAuditLogger;

    public AdminCommunityMemoServiceImpl(AdminCommunityMemoRepository adminCommunityMemoRepository,
        MinioPublicUrlResolver minioPublicUrlResolver, ObjectMapper objectMapper, AdminAuditLogger adminAuditLogger) {
        this.adminCommunityMemoRepository = adminCommunityMemoRepository;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.objectMapper = objectMapper;
        this.adminAuditLogger = adminAuditLogger;
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 운영 필터와 페이징 조건으로 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoListResponse getCommunityMemos(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, String pageValue,
        String sizeValue) {
        requireAdmin(adminPrincipal);

        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedModerationStatus = normalizeModerationStatus(moderationStatus);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedKeyword = normalizeKeyword(keyword);

        long totalElements = adminCommunityMemoRepository.countMemos(hidden, normalizedModerationStatus,
            normalizedSourceType, reported, normalizedKeyword);
        List<AdminCommunityMemoItemResponse> items = adminCommunityMemoRepository
            .findMemos(hidden, normalizedModerationStatus, normalizedSourceType, reported, normalizedKeyword, size,
                calculateOffset(page, size))
            .stream().map(this::toItemResponse).toList();

        return new AdminCommunityMemoListResponse(items, page, size, totalElements,
            calculateHasNext(page, size, totalElements));
    }

    /**
     * hidden 메모를 포함해 삭제되지 않은 커뮤니티 메모 상세를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoDetailResponse getCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue) {
        requireAdmin(adminPrincipal);
        UUID memoId = parseMemoId(memoIdValue);

        return adminCommunityMemoRepository.findMemoById(memoId).map(this::toDetailResponse)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
    }

    /**
     * hidden 메모를 포함해 삭제되지 않은 커뮤니티 메모의 신고 내역을 최신순으로 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminCommunityMemoReportListResponse getCommunityMemoReports(AdminPrincipal adminPrincipal,
        String memoIdValue, String reasonValue, String pageValue, String sizeValue) {
        requireAdmin(adminPrincipal);
        UUID memoId = parseMemoId(memoIdValue);
        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedReason = normalizeReportReason(reasonValue);

        if (!adminCommunityMemoRepository.existsMemoById(memoId)) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        long totalElements = adminCommunityMemoRepository.countMemoReports(memoId, normalizedReason);
        List<AdminCommunityMemoReportItemResponse> items = adminCommunityMemoRepository
            .findMemoReports(memoId, normalizedReason, size, calculateOffset(page, size)).stream()
            .map(this::toReportItemResponse).toList();

        return new AdminCommunityMemoReportListResponse(items, page, size, totalElements,
            calculateHasNext(page, size, totalElements));
    }

    /**
     * visible 메모를 관리자 수동 숨김 상태로 전환합니다. 이미 hidden이면 상태를 유지하고 상세를 반환합니다.
     */
    @Override
    @Transactional
    public AdminCommunityMemoDetailResponse hideCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        UUID memoId = parseMemoId(memoIdValue);
        String reason = validateReviewReason(request, INVALID_HIDE_REASON_MESSAGE);

        AdminCommunityMemoRow row = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (row.hidden()) {
            emitAfterCommit(
                () -> adminAuditLogger.logMemoSoftDelete(adminPrincipal, memoId.toString(), reason, clientInfo, false));
            return toDetailResponse(row);
        }

        LocalDateTime hiddenAt = LocalDateTime.now();
        int updatedCount = adminCommunityMemoRepository.hideMemo(memoId, adminPrincipal.id(), hiddenAt);
        if (updatedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        AdminCommunityMemoDetailResponse response = adminCommunityMemoRepository.findMemoById(memoId)
            .map(this::toDetailResponse).orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        emitAfterCommit(
            () -> adminAuditLogger.logMemoSoftDelete(adminPrincipal, memoId.toString(), reason, clientInfo, true));

        return response;
    }

    /**
     * hidden 메모를 visible 상태로 복구합니다. 이미 visible이면 상태를 유지하고 상세를 반환합니다.
     */
    @Override
    @Transactional
    public AdminCommunityMemoDetailResponse restoreCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminCommunityMemoReviewRequest request, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        UUID memoId = parseMemoId(memoIdValue);
        String reason = validateReviewReason(request, INVALID_RESTORE_REASON_MESSAGE);

        AdminCommunityMemoRow row = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (!row.hidden()) {
            emitAfterCommit(() -> adminAuditLogger.logMemoRestore(adminPrincipal, memoId.toString(), reason, clientInfo,
                false, null));
            return toDetailResponse(row);
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedCount = adminCommunityMemoRepository.restoreMemo(memoId, adminPrincipal.id(), updatedAt);
        if (updatedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        AdminCommunityMemoDetailResponse response = adminCommunityMemoRepository.findMemoById(memoId)
            .map(this::toDetailResponse).orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        emitAfterCommit(() -> adminAuditLogger.logMemoRestore(adminPrincipal, memoId.toString(), reason, clientInfo,
            true, row.hiddenReason()));

        return response;
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

    private UUID parseMemoId(String memoIdValue) {
        if (!StringUtils.hasText(memoIdValue)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(memoIdValue.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    private String normalizeModerationStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        return CommunityMemoModerationStatus.findByValue(normalizedValue)
            .orElseThrow(() -> new BadRequestException(INVALID_QUERY_MESSAGE)).value();
    }

    private String normalizeSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        return CommunityMemoSourceType.findByValue(normalizedValue)
            .orElseThrow(() -> new BadRequestException(INVALID_QUERY_MESSAGE)).value();
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeReportReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        return CommunityMemoReportReason.findByValue(normalizedValue)
            .orElseThrow(() -> new BadRequestException(INVALID_REPORT_REASON_MESSAGE)).value();
    }

    private int parsePage(String pageValue) {
        int page = parseIntegerOrDefault(pageValue, DEFAULT_PAGE);
        if (page < 0) {
            throw new BadRequestException(INVALID_QUERY_MESSAGE);
        }

        return page;
    }

    private int parseSize(String sizeValue) {
        int size = parseIntegerOrDefault(sizeValue, DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(INVALID_QUERY_MESSAGE);
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
            throw new BadRequestException(INVALID_QUERY_MESSAGE);
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }

    private String validateReviewReason(AdminCommunityMemoReviewRequest request, String message) {
        String reason = request == null ? null : request.reason();
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(message);
        }

        return reason.trim();
    }

    private AdminCommunityMemoItemResponse toItemResponse(AdminCommunityMemoRow row) {
        ImageUrls imageUrls = resolveImageUrls(row);

        return new AdminCommunityMemoItemResponse(row.memoId().toString(), row.userId().toString(),
            row.authorNickname(), resolveSourceType(row), stringify(row.artifactId()), row.artifactKind(),
            imageUrls.representative(), imageUrls.original(), imageUrls.thumbnail(), row.positionX(), row.positionY(),
            row.zIndex(), row.rotationDeg(), row.reportCount(), row.hidden(), row.hiddenReason(), row.hiddenAt(),
            row.moderationStatus(), row.ocrText(), row.ocrCategories(), row.reviewedBy(), row.reviewedAt(),
            row.attachedAt(), row.createdAt(), row.updatedAt());
    }

    private AdminCommunityMemoDetailResponse toDetailResponse(AdminCommunityMemoRow row) {
        ImageUrls imageUrls = resolveImageUrls(row);

        return new AdminCommunityMemoDetailResponse(row.memoId().toString(), row.userId().toString(),
            row.authorNickname(), resolveSourceType(row), stringify(row.artifactId()), row.artifactKind(),
            imageUrls.representative(), imageUrls.original(), imageUrls.thumbnail(), row.positionX(), row.positionY(),
            row.zIndex(), row.rotationDeg(), parseDecoration(row.decoration()), row.reportCount(),
            findReportItemResponses(row.memoId()), row.hidden(), row.hiddenReason(), row.hiddenAt(),
            row.moderationStatus(), row.ocrText(), row.ocrCategories(), row.reviewedBy(), row.reviewedAt(),
            row.attachedAt(), row.createdAt(), row.updatedAt());
    }

    private AdminCommunityMemoReportItemResponse toReportItemResponse(AdminCommunityMemoReportRow row) {
        return new AdminCommunityMemoReportItemResponse(row.reportId(), row.memoId().toString(),
            row.reporterUserId().toString(), row.reporterNickname(), row.reason(), row.reasonDetail(), row.createdAt());
    }

    private List<AdminCommunityMemoReportItemResponse> findReportItemResponses(UUID memoId) {
        return adminCommunityMemoRepository.findMemoReports(memoId).stream().map(this::toReportItemResponse).toList();
    }

    private ImageUrls resolveImageUrls(AdminCommunityMemoRow row) {
        String originalUrl = minioPublicUrlResolver.resolve(row.originalImageReference());
        String thumbnailUrl = minioPublicUrlResolver.resolve(row.thumbnailImageReference());
        String representativeUrl = thumbnailUrl == null ? originalUrl : thumbnailUrl;

        return new ImageUrls(originalUrl, thumbnailUrl, representativeUrl);
    }

    private String resolveSourceType(AdminCommunityMemoRow row) {
        return row.artifactId() == null
            ? CommunityMemoSourceType.DIRECT.value()
            : CommunityMemoSourceType.GALLERY.value();
    }

    private String stringify(UUID value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> parseDecoration(String decoration) {
        if (!StringUtils.hasText(decoration)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedDecoration = objectMapper.readValue(decoration, DECORATION_TYPE);
            return parsedDecoration == null ? Map.of() : parsedDecoration;
        } catch (JsonProcessingException e) {
            log.warn("관리자 커뮤니티 메모 decoration JSON을 파싱할 수 없습니다. decoration={}", decoration, e);

            return Map.of();
        }
    }

    private record ImageUrls(String original, String thumbnail, String representative) {
    }
}
