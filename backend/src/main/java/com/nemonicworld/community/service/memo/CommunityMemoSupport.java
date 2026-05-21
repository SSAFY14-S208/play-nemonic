package com.nemonicworld.community.service.memo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoSourceGalleryRow;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 유스케이스들이 공유하는 검증, 조회, 실패 로그 helper입니다.
 */
@Component
class CommunityMemoSupport {

    static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    static final String INVALID_MEMO_SOURCE_MESSAGE = "커뮤니티 메모 원본 정보가 올바르지 않습니다.";
    static final String INVALID_ORIGINAL_FILE_ID_MESSAGE = "유효하지 않은 originalFileId 형식입니다.";
    static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    static final String INVALID_SOURCE_GALLERY_ID_MESSAGE = "유효하지 않은 sourceGalleryId 형식입니다.";
    static final String DUPLICATED_FILE_MESSAGE = "커뮤니티 메모 원본과 썸네일 파일은 서로 달라야 합니다.";
    static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    static final String INVALID_POSITION_MESSAGE = "커뮤니티 메모 위치 정보가 올바르지 않습니다.";
    static final String MEMO_ACCESS_DENIED_MESSAGE = "커뮤니티 메모 위치를 수정할 권한이 없습니다.";
    static final String MEMO_DELETE_ACCESS_DENIED_MESSAGE = "커뮤니티 메모를 삭제할 권한이 없습니다.";
    static final String OWN_MEMO_REPORT_MESSAGE = "본인 메모는 신고할 수 없습니다.";
    static final String DUPLICATE_REPORT_MESSAGE = "이미 신고한 커뮤니티 메모입니다.";

    private static final String UNSUPPORTED_SOURCE_TYPE_MESSAGE = "지원하지 않는 커뮤니티 메모 sourceType입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";
    private static final String INVALID_REPORT_REASON_MESSAGE = "커뮤니티 메모 신고 사유가 올바르지 않습니다.";
    private static final String INVALID_DECORATION_MESSAGE = "커뮤니티 메모 데코레이션 정보가 올바르지 않습니다.";
    private static final String EMPTY_DECORATION_JSON = "{}";

    private final CommunityMemoRepository communityMemoRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileUploadRepository fileUploadRepository;
    private final ObjectMapper objectMapper;

    CommunityMemoSupport(CommunityMemoRepository communityMemoRepository, AnonymousUserResolver anonymousUserResolver,
        FileUploadRepository fileUploadRepository, ObjectMapper objectMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileUploadRepository = fileUploadRepository;
        this.objectMapper = objectMapper;
    }

    UUID parseUserUuid(String userUuidValue) {
        return anonymousUserResolver.parseUuid(userUuidValue);
    }

    void resolveUser(UUID userUuid) {
        anonymousUserResolver.resolve(userUuid);
    }

    UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName) {
        return findVisibleMemoOrLogNotFound(memoId, actorUuid, eventName, -1L);
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName, long startedAt) {
        return communityMemoRepository.findVisibleMemoById(memoId).orElseThrow(() -> {
            String reasonCode = resolveNotVisibleReasonCode(memoId);
            Map<String, Object> eventMetadata = metadata("memo_id", memoId, "reason_code", reasonCode, "status",
                "not_found");
            if (startedAt >= 0) {
                eventMetadata.put("duration_ms", calculateLatencyMs(startedAt));
            }
            CommunityMemoEventLogger.business(eventName, actorUuid, eventMetadata);
            logHiddenOrDeletedAccessAttempt(actorUuid, eventMetadata);
            return new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        });
    }

    void logCreateValidationFailed(String userUuidValue, CommunityMemoCreateRequest request, RuntimeException error,
        String moderationBlockedMessage, String moderationUnavailableMessage) {
        if (moderationBlockedMessage.equals(error.getMessage())
            || moderationUnavailableMessage.equals(error.getMessage())) {
            return;
        }

        UUID userUuid = parseUuidQuietly(userUuidValue);
        CommunityMemoEventLogger.business("community_memo_create_validation_failed", userUuid,
            metadata("source_type", request == null ? null : request.sourceType(), "original_file_id",
                request == null ? null : request.originalFileId(), "thumbnail_file_id",
                request == null ? null : request.thumbnailFileId(), "source_gallery_id",
                request == null ? null : request.sourceGalleryId(), "reason_code", exceptionReasonCode(error),
                "message", error.getMessage()));
        logCreateSecurityEvent(userUuid, request, error);
    }

    void logCommunityActionFailed(String eventName, String memoIdValue, String userUuidValue, RuntimeException error) {
        UUID actorUuid = parseUuidQuietly(userUuidValue);
        Map<String, Object> eventMetadata = metadata("memo_id", memoIdValue, "reason_code", exceptionReasonCode(error),
            "message", error.getMessage());
        if (memoIdValue != null) {
            UUID memoId = parseUuidQuietly(memoIdValue);
            if (memoId != null) {
                eventMetadata.put("visibility_reason_code", resolveNotVisibleReasonCode(memoId));
            }
        }
        CommunityMemoEventLogger.business(eventName, actorUuid, eventMetadata);
        logCommunitySecurityEvent(eventName, actorUuid, eventMetadata, error);
    }

    CommunityMemoReportReason validateReportReason(CommunityMemoReportRequest request) {
        String reason = request == null ? null : request.reason();
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(INVALID_REPORT_REASON_MESSAGE);
        }

        return CommunityMemoReportReason.findByDisplayName(reason)
            .orElseThrow(() -> new BadRequestException(INVALID_REPORT_REASON_MESSAGE));
    }

    String normalizeReasonDetail(String reasonDetail) {
        return StringUtils.hasText(reasonDetail) ? reasonDetail.trim() : null;
    }

    CommunityMemoSourceType validateSourceType(CommunityMemoCreateRequest request) {
        String sourceType = request == null ? null : request.sourceType();
        if (CommunityMemoSourceType.DIRECT.value().equals(sourceType)) {
            if (StringUtils.hasText(request.sourceGalleryId())) {
                throw new BadRequestException(INVALID_MEMO_SOURCE_MESSAGE);
            }

            return CommunityMemoSourceType.DIRECT;
        }

        if (CommunityMemoSourceType.GALLERY.value().equals(sourceType)) {
            if (!StringUtils.hasText(request.sourceGalleryId())) {
                throw new BadRequestException(INVALID_SOURCE_GALLERY_ID_MESSAGE);
            }

            return CommunityMemoSourceType.GALLERY;
        }

        throw new BadRequestException(UNSUPPORTED_SOURCE_TYPE_MESSAGE);
    }

    UUID resolveSourceArtifactId(CommunityMemoSourceType sourceType, String sourceGalleryIdValue, UUID userUuid) {
        if (sourceType == CommunityMemoSourceType.DIRECT) {
            return null;
        }

        UUID sourceGalleryId = parseFileId(sourceGalleryIdValue, INVALID_SOURCE_GALLERY_ID_MESSAGE);
        CommunityMemoSourceGalleryRow sourceGallery = communityMemoRepository
            .findActiveSourceGallery(sourceGalleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));

        return sourceGallery.artifactId();
    }

    UUID parseFileId(String fileIdValue, String invalidMessage) {
        if (!StringUtils.hasText(fileIdValue)) {
            throw new BadRequestException(invalidMessage);
        }

        try {
            return UUID.fromString(fileIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(invalidMessage);
        }
    }

    void validateDifferentFiles(UUID originalFileId, UUID thumbnailFileId) {
        if (originalFileId.equals(thumbnailFileId)) {
            throw new BadRequestException(DUPLICATED_FILE_MESSAGE);
        }
    }

    void validatePosition(CommunityMemoCreateRequest request) {
        if (request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(INVALID_POSITION_MESSAGE);
        }
    }

    void validateLayout(CommunityMemoLayoutUpdateRequest request) {
        if (request == null || request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(INVALID_POSITION_MESSAGE);
        }
    }

    void validateCommunityFile(FileUpload fileUpload, UUID userUuid) {
        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.hasPurpose(FileUploadPurpose.COMMUNITY)) {
            throw new BadRequestException(INVALID_MEMO_SOURCE_MESSAGE);
        }

        if (fileUpload.isDeleted() || !fileUpload.isUploaded()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }
    }

    FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    String serializeDecoration(JsonNode decoration) {
        if (decoration == null || decoration.isNull()) {
            return EMPTY_DECORATION_JSON;
        }

        if (!decoration.isObject()) {
            throw new BadRequestException(INVALID_DECORATION_MESSAGE);
        }

        try {
            return objectMapper.writeValueAsString(decoration);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_DECORATION_MESSAGE);
        }
    }

    long calculateLatencyMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return viewerUserUuid != null && viewerUserUuid.equals(userId);
    }

    private String resolveNotVisibleReasonCode(UUID memoId) {
        return communityMemoRepository.findMemoVisibilityById(memoId).map(row -> {
            if (row.deletedAt() != null) {
                return "deleted";
            }
            if (row.hidden()) {
                return "hidden";
            }

            return "not_visible";
        }).orElse("not_found");
    }

    private void logCreateSecurityEvent(UUID actorUuid, CommunityMemoCreateRequest request, RuntimeException error) {
        if (error instanceof ForbiddenException) {
            CommunityMemoEventLogger
                .warn("community_file_ownership_violation", "community file ownership violation", actorUuid,
                    metadata("original_file_id", request == null ? null : request.originalFileId(), "thumbnail_file_id",
                        request == null ? null : request.thumbnailFileId(), "reason_code", "file_owner_mismatch"),
                    error);
            return;
        }

        if (error instanceof NotFoundException && "존재하지 않는 사용자입니다.".equals(error.getMessage())) {
            CommunityMemoEventLogger.warn("community_user_not_found", "community user not found", actorUuid,
                metadata("reason_code", "user_not_found"), error);
        }
    }

    private void logCommunitySecurityEvent(String eventName, UUID actorUuid, Map<String, Object> eventMetadata,
        RuntimeException error) {
        if (error instanceof ForbiddenException) {
            CommunityMemoEventLogger.warn("community_ownership_violation", "community ownership violation", actorUuid,
                eventMetadata, error);
            return;
        }

        if (error instanceof ConflictException && "community_memo_report_rejected".equals(eventName)) {
            CommunityMemoEventLogger.warn("community_duplicate_report_attempt", "community duplicate report attempt",
                actorUuid, eventMetadata, error);
            return;
        }

        if (error instanceof NotFoundException) {
            logHiddenOrDeletedAccessAttempt(actorUuid, eventMetadata);
            if ("존재하지 않는 사용자입니다.".equals(error.getMessage())) {
                CommunityMemoEventLogger.warn("community_user_not_found", "community user not found", actorUuid,
                    eventMetadata, error);
            }
        }
    }

    private void logHiddenOrDeletedAccessAttempt(UUID actorUuid, Map<String, Object> eventMetadata) {
        Object reasonCode = eventMetadata.get("visibility_reason_code");
        if (reasonCode == null) {
            reasonCode = eventMetadata.get("reason_code");
        }

        if ("hidden".equals(reasonCode)) {
            CommunityMemoEventLogger.warn("community_hidden_memo_access_attempt",
                "community hidden memo access attempt", actorUuid, eventMetadata, null);
            return;
        }

        if ("deleted".equals(reasonCode)) {
            CommunityMemoEventLogger.warn("community_deleted_memo_access_attempt",
                "community deleted memo access attempt", actorUuid, eventMetadata, null);
        }
    }

    private UUID parseUuidQuietly(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String exceptionReasonCode(RuntimeException error) {
        if (error instanceof BadRequestException) {
            return "bad_request";
        }
        if (error instanceof ForbiddenException) {
            return "forbidden";
        }
        if (error instanceof NotFoundException) {
            return "not_found";
        }
        if (error instanceof ConflictException) {
            return "conflict";
        }

        return error.getClass().getSimpleName();
    }
}
