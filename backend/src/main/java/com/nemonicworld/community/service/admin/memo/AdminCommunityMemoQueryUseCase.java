package com.nemonicworld.community.service.admin.memo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
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
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminCommunityMemoQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdminCommunityMemoQueryUseCase.class);
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    private static final String INVALID_QUERY_MESSAGE = "관리자 커뮤니티 메모 조회 조건이 올바르지 않습니다.";
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

    public AdminCommunityMemoQueryUseCase(AdminCommunityMemoRepository adminCommunityMemoRepository,
        MinioPublicUrlResolver minioPublicUrlResolver, ObjectMapper objectMapper, AdminAuditLogger adminAuditLogger) {
        this.adminCommunityMemoRepository = adminCommunityMemoRepository;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.objectMapper = objectMapper;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Transactional(readOnly = true)
    public AdminCommunityMemoListResponse getCommunityMemos(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, String pageValue,
        String sizeValue, AdminClientInfo clientInfo) {
        try {
            return getCommunityMemosInternal(adminPrincipal, hidden, moderationStatus, sourceType, reported, keyword,
                pageValue, sizeValue, clientInfo);
        } catch (RuntimeException e) {
            adminAuditLogger.logCommunityMemoSearchFailed(adminPrincipal, clientInfo, "view", "community_memo_list",
                exceptionReasonCode(e));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AdminCommunityMemoDetailResponse getCommunityMemo(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminClientInfo clientInfo) {
        try {
            return getCommunityMemoInternal(adminPrincipal, memoIdValue, clientInfo);
        } catch (RuntimeException e) {
            adminAuditLogger.logCommunityMemoSearchFailed(adminPrincipal, clientInfo, "view_detail", memoIdValue,
                exceptionReasonCode(e));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AdminCommunityMemoReportListResponse getCommunityMemoReports(AdminPrincipal adminPrincipal,
        String memoIdValue, String reasonValue, String pageValue, String sizeValue, AdminClientInfo clientInfo) {
        try {
            return getCommunityMemoReportsInternal(adminPrincipal, memoIdValue, reasonValue, pageValue, sizeValue,
                clientInfo);
        } catch (RuntimeException e) {
            adminAuditLogger.logCommunityMemoSearchFailed(adminPrincipal, clientInfo, "view_reports", memoIdValue,
                exceptionReasonCode(e));
            throw e;
        }
    }

    AdminCommunityMemoDetailResponse toDetailResponse(AdminCommunityMemoRow row) {
        ImageUrls imageUrls = resolveImageUrls(row);

        return new AdminCommunityMemoDetailResponse(row.memoId().toString(), row.userId().toString(),
            row.authorNickname(), resolveSourceType(row), stringify(row.artifactId()), row.artifactKind(),
            imageUrls.representative(), imageUrls.original(), imageUrls.thumbnail(), row.positionX(), row.positionY(),
            row.zIndex(), row.rotationDeg(), parseDecoration(row.decoration()), row.reportCount(),
            findReportItemResponses(row.memoId()), row.hidden(), row.hiddenReason(), row.hiddenAt(),
            row.moderationStatus(), row.ocrText(), row.ocrCategories(), row.reviewedBy(), row.reviewedAt(),
            row.attachedAt(), row.createdAt(), row.updatedAt());
    }

    UUID parseMemoId(String memoIdValue) {
        if (!StringUtils.hasText(memoIdValue)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(memoIdValue.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    private AdminCommunityMemoListResponse getCommunityMemosInternal(AdminPrincipal adminPrincipal, Boolean hidden,
        String moderationStatus, String sourceType, Boolean reported, String keyword, String pageValue,
        String sizeValue, AdminClientInfo clientInfo) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

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

        AdminCommunityMemoListResponse response = new AdminCommunityMemoListResponse(items, page, size, totalElements,
            calculateHasNext(page, size, totalElements));
        adminAuditLogger.logCommunityMemoListViewed(adminPrincipal, clientInfo, hidden, normalizedModerationStatus,
            normalizedSourceType, reported, normalizedKeyword, page, size, totalElements);

        return response;
    }

    private AdminCommunityMemoDetailResponse getCommunityMemoInternal(AdminPrincipal adminPrincipal, String memoIdValue,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);
        UUID memoId = parseMemoId(memoIdValue);

        AdminCommunityMemoRow row = adminCommunityMemoRepository.findMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        AdminCommunityMemoDetailResponse response = toDetailResponse(row);
        adminAuditLogger.logCommunityMemoDetailViewed(adminPrincipal, clientInfo, memoId.toString(), row.hidden(),
            row.reportCount());

        return response;
    }

    private AdminCommunityMemoReportListResponse getCommunityMemoReportsInternal(AdminPrincipal adminPrincipal,
        String memoIdValue, String reasonValue, String pageValue, String sizeValue, AdminClientInfo clientInfo) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);
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

        AdminCommunityMemoReportListResponse response = new AdminCommunityMemoReportListResponse(items, page, size,
            totalElements, calculateHasNext(page, size, totalElements));
        adminAuditLogger.logCommunityMemoReportsViewed(adminPrincipal, clientInfo, memoId.toString(), normalizedReason,
            page, size, totalElements);

        return response;
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

    private String exceptionReasonCode(RuntimeException error) {
        if (error instanceof BadRequestException) {
            return "bad_request";
        }
        if (error instanceof NotFoundException) {
            return "not_found";
        }
        if (error instanceof UnauthorizedException) {
            return "unauthorized";
        }

        return error.getClass().getSimpleName();
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
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

    private AdminCommunityMemoReportItemResponse toReportItemResponse(AdminCommunityMemoReportRow row) {
        return new AdminCommunityMemoReportItemResponse(row.reportId(), row.memoId().toString(),
            row.reporterUserId().toString(), row.reporterNickname(), row.reason(), row.reasonDetail(), row.createdAt());
    }

    private List<AdminCommunityMemoReportItemResponse> findReportItemResponses(UUID memoId) {
        return adminCommunityMemoRepository.findMemoReports(memoId).stream().map(this::toReportItemResponse).toList();
    }

    private ImageUrls resolveImageUrls(AdminCommunityMemoRow row) {
        String originalUrl = resolveMemoImageUrl(row.originalImageReference(), row.memoId(), "original");
        String thumbnailUrl = resolveMemoImageUrl(row.thumbnailImageReference(), row.memoId(), "thumbnail");
        String representativeUrl = thumbnailUrl == null ? originalUrl : thumbnailUrl;

        return new ImageUrls(originalUrl, thumbnailUrl, representativeUrl);
    }

    private String resolveMemoImageUrl(String objectKey, UUID memoId, String imageRole) {
        String imageUrl = minioPublicUrlResolver.resolve(objectKey);
        if (StringUtils.hasText(objectKey) && !StringUtils.hasText(imageUrl)) {
            CommunityMemoEventLogger.warn("community_file_url_resolve_failed",
                "admin community memo image url resolve failed", null, CommunityMemoEventLogger.metadata("memo_id",
                    memoId, "image_role", imageRole, "object_key_hash", CommunityMemoEventLogger.hash(objectKey)),
                null);
        }

        return imageUrl;
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
            CommunityMemoEventLogger.warn("community_decoration_parse_failed",
                "admin community memo decoration parse failed", CommunityMemoEventLogger.metadata("decoration_length",
                    decoration.length(), "reason_code", "parse_failed"),
                e);
            log.warn("admin community memo decoration JSON parse failed. decorationLength={}", decoration.length(), e);

            return Map.of();
        }
    }

    private record ImageUrls(String original, String thumbnail, String representative) {
    }
}
