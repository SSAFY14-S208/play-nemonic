package com.nemonicworld.community.service.memo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoCreateCommand;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationException;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationRequest;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationResult;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import com.nemonicworld.community.service.support.CommunityRuntimeSettingsProvider;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 생성, 게시 전 파일 검증, 모더레이션, FIFO 만료 유스케이스입니다.
 */
@Service
class CommunityMemoCreateUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommunityMemoCreateUseCase.class);
    private static final String MODERATION_BLOCKED_MESSAGE = "부적절한 표현이 감지되어 게시할 수 없습니다.";
    private static final String MODERATION_UNAVAILABLE_MESSAGE = "커뮤니티 메모 모더레이션을 완료할 수 없습니다.";
    private static final long MODERATION_SLOW_LOG_THRESHOLD_MS = 30_000L;

    private final CommunityMemoRepository communityMemoRepository;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final CommunityMemoModerationClient communityMemoModerationClient;
    private final CommunityRuntimeSettingsProvider communityRuntimeSettingsProvider;
    private final ObjectMapper objectMapper;
    private final CommunityMemoSupport communityMemoSupport;
    private final CommunityMemoResponseMapper communityMemoResponseMapper;

    CommunityMemoCreateUseCase(CommunityMemoRepository communityMemoRepository,
        MinioPublicUrlResolver minioPublicUrlResolver, CommunityMemoModerationClient communityMemoModerationClient,
        CommunityRuntimeSettingsProvider communityRuntimeSettingsProvider, ObjectMapper objectMapper,
        CommunityMemoSupport communityMemoSupport, CommunityMemoResponseMapper communityMemoResponseMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.communityMemoModerationClient = communityMemoModerationClient;
        this.communityRuntimeSettingsProvider = communityRuntimeSettingsProvider;
        this.objectMapper = objectMapper;
        this.communityMemoSupport = communityMemoSupport;
        this.communityMemoResponseMapper = communityMemoResponseMapper;
    }

    @Transactional
    CommunityMemoDetailResponse createCommunityMemo(String userUuidValue, CommunityMemoCreateRequest request) {
        try {
            return createCommunityMemoInternal(userUuidValue, request);
        } catch (RuntimeException e) {
            communityMemoSupport.logCreateValidationFailed(userUuidValue, request, e, MODERATION_BLOCKED_MESSAGE,
                MODERATION_UNAVAILABLE_MESSAGE);
            throw e;
        }
    }

    private CommunityMemoDetailResponse createCommunityMemoInternal(String userUuidValue,
        CommunityMemoCreateRequest request) {
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);
        CommunityMemoSourceType sourceType = communityMemoSupport.validateSourceType(request);
        UUID sourceArtifactId = communityMemoSupport.resolveSourceArtifactId(sourceType, request.sourceGalleryId(),
            userUuid);

        UUID originalFileId = communityMemoSupport.parseFileId(request.originalFileId(),
            CommunityMemoSupport.INVALID_ORIGINAL_FILE_ID_MESSAGE);
        UUID thumbnailFileId = communityMemoSupport.parseFileId(request.thumbnailFileId(),
            CommunityMemoSupport.INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        communityMemoSupport.validateDifferentFiles(originalFileId, thumbnailFileId);
        communityMemoSupport.validatePosition(request);
        CommunityMemoEventLogger.business("community_memo_create_requested", userUuid,
            metadata("source_type", sourceType.value(), "original_file_id", originalFileId, "thumbnail_file_id",
                thumbnailFileId, "source_gallery_id", request.sourceGalleryId(), "source_artifact_id",
                sourceArtifactId));

        String decorationJson = communityMemoSupport.serializeDecoration(request.decoration());
        FileUpload originalFileUpload = communityMemoSupport.findFileUpload(originalFileId);
        FileUpload thumbnailFileUpload = communityMemoSupport.findFileUpload(thumbnailFileId);
        communityMemoSupport.validateCommunityFile(originalFileUpload, userUuid);
        communityMemoSupport.validateCommunityFile(thumbnailFileUpload, userUuid);

        UUID memoId = UUID.randomUUID();
        String bodyObjectKey = originalFileUpload.getObjectKey();
        String thumbnailObjectKey = thumbnailFileUpload.getObjectKey();
        String originalImageUrl = minioPublicUrlResolver.resolve(bodyObjectKey);
        String thumbnailImageUrl = minioPublicUrlResolver.resolve(thumbnailObjectKey);
        if (!StringUtils.hasText(originalImageUrl) || !StringUtils.hasText(thumbnailImageUrl)) {
            throw new BadRequestException(CommunityMemoSupport.INVALID_MEMO_SOURCE_MESSAGE);
        }

        String clientText = normalizeClientText(request.clientText());
        CommunityMemoEventLogger.business("community_memo_moderation_requested", userUuid,
            metadata("source_type", sourceType.value(), "source_artifact_id", sourceArtifactId, "original_file_id",
                originalFileId, "thumbnail_file_id", thumbnailFileId, "original_image_available",
                StringUtils.hasText(originalImageUrl), "thumbnail_image_available",
                StringUtils.hasText(thumbnailImageUrl), "client_text_length",
                CommunityMemoEventLogger.textLength(clientText)));
        CommunityMemoModerationResult moderationResult = checkModeration(originalImageUrl, thumbnailImageUrl,
            clientText, sourceType, userUuid);

        LocalDateTime now = LocalDateTime.now();
        CommunityMemoCreateCommand command = new CommunityMemoCreateCommand(memoId, userUuid, sourceArtifactId,
            bodyObjectKey, thumbnailObjectKey, request.positionX(), request.positionY(), request.zIndex(),
            request.rotationDeg().floatValue(), decorationJson, moderationResult.ocrText(),
            serializeModerationCategories(moderationResult.categories()), now, now, now, now);
        communityMemoRepository.insertMemo(command);
        expireOverflowVisibleMemos(memoId, now);

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            "community_memo_create_validation_failed");
        CommunityMemoEventLogger.business("community_memo_created", userUuid,
            metadata("memo_id", memoId, "source_type", sourceType.value(), "artifact_id", sourceArtifactId,
                "original_file_id", originalFileId, "thumbnail_file_id", thumbnailFileId, "report_count",
                row.reportCount(), "moderation_status", row.moderationStatus(), "body_image_object_key_hash",
                CommunityMemoEventLogger.hash(bodyObjectKey), "thumbnail_image_object_key_hash",
                CommunityMemoEventLogger.hash(thumbnailObjectKey)));
        return communityMemoResponseMapper.toDetailResponse(row, userUuid);
    }

    private void expireOverflowVisibleMemos(UUID newMemoId, LocalDateTime now) {
        int visibleMemoCount = communityMemoRepository.countVisibleMemos();
        int maxVisibleMemoCount = communityRuntimeSettingsProvider.currentMaxVisibleMemoCount();
        int overflowCount = visibleMemoCount - maxVisibleMemoCount;
        CommunityMemoEventLogger.business("community_memo_fifo_checked",
            metadata("new_memo_id", newMemoId, "visible_memo_count", visibleMemoCount, "max_visible_memo_count",
                maxVisibleMemoCount, "overflow_count", Math.max(overflowCount, 0)));
        if (overflowCount <= 0) {
            CommunityMemoEventLogger.business("community_memo_fifo_skipped", metadata("new_memo_id", newMemoId,
                "visible_memo_count", visibleMemoCount, "limit", maxVisibleMemoCount));
            return;
        }

        List<UUID> expiredMemoIds = communityMemoRepository.findOldestVisibleMemoIdsForExpiry(newMemoId, overflowCount);
        int expiredCount = communityMemoRepository.expireVisibleMemosByIds(expiredMemoIds, now);
        CommunityMemoEventLogger.business("community_memo_fifo_expired",
            metadata("new_memo_id", newMemoId, "requested_expire_count", overflowCount, "expired_count", expiredCount,
                "expired_memo_ids", expiredMemoIds, "deleted_reason", "expired"));
    }

    private CommunityMemoModerationResult checkModeration(String originalImageUrl, String thumbnailImageUrl,
        String clientText, CommunityMemoSourceType sourceType, UUID userUuid) {
        long startedAt = System.nanoTime();
        try {
            CommunityMemoModerationResult result = communityMemoModerationClient
                .check(new CommunityMemoModerationRequest(originalImageUrl, thumbnailImageUrl, clientText,
                    sourceType.value()));
            long latencyMs = communityMemoSupport.calculateLatencyMs(startedAt);
            logModerationResult(result, clientText, sourceType, userUuid, latencyMs);
            logSlowModerationIfNeeded(sourceType, userUuid, latencyMs);
            if (!result.allowed()) {
                throw new BadRequestException(MODERATION_BLOCKED_MESSAGE);
            }

            return result;
        } catch (CommunityMemoModerationException e) {
            long latencyMs = communityMemoSupport.calculateLatencyMs(startedAt);
            CommunityMemoEventLogger
                .warn("community_memo_moderation_failed", "community memo moderation failed", userUuid,
                    metadata("source_type", sourceType.value(), "client_text_length",
                        CommunityMemoEventLogger.textLength(clientText), "latency_ms", latencyMs, "fail_closed", true),
                    e);
            logSlowModerationIfNeeded(sourceType, userUuid, latencyMs);
            throw new BadRequestException(MODERATION_UNAVAILABLE_MESSAGE);
        }
    }

    private String normalizeClientText(String clientText) {
        return clientText == null ? "" : clientText;
    }

    private void logModerationResult(CommunityMemoModerationResult result, String clientText,
        CommunityMemoSourceType sourceType, UUID userUuid, long latencyMs) {
        String categories = result.categories() == null || result.categories().isNull()
            ? "[]"
            : result.categories().toString();
        log.info("커뮤니티 메모 모더레이션 결과 allowed={} clientTextLength={} checkedTextLength={} categories={}", result.allowed(),
            CommunityMemoEventLogger.textLength(clientText), CommunityMemoEventLogger.textLength(result.ocrText()),
            categories);
        CommunityMemoEventLogger.business(
            result.allowed() ? "community_memo_moderation_allowed" : "community_memo_moderation_blocked", userUuid,
            metadata("source_type", sourceType.value(), "allowed", result.allowed(), "client_text_length",
                CommunityMemoEventLogger.textLength(clientText), "checked_text_length",
                CommunityMemoEventLogger.textLength(result.ocrText()), "categories", categories, "latency_ms",
                latencyMs, "checked_at", LocalDateTime.now()));
    }

    private void logSlowModerationIfNeeded(CommunityMemoSourceType sourceType, UUID userUuid, long latencyMs) {
        if (latencyMs <= MODERATION_SLOW_LOG_THRESHOLD_MS) {
            return;
        }

        CommunityMemoEventLogger
            .warn(
                "community_memo_moderation_slow", "community memo moderation is slow", userUuid, metadata("source_type",
                    sourceType.value(), "latency_ms", latencyMs, "threshold_ms", MODERATION_SLOW_LOG_THRESHOLD_MS),
                null);
    }

    private String serializeModerationCategories(JsonNode categories) {
        if (categories == null || categories.isNull()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(categories);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(MODERATION_UNAVAILABLE_MESSAGE);
        }
    }
}
