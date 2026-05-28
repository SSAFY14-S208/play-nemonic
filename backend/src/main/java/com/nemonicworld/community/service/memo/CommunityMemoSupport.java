package com.nemonicworld.community.service.memo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.files.entity.FileUpload;
import java.util.UUID;
import org.springframework.stereotype.Component;

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

    private final CommunityMemoUserSupport communityMemoUserSupport;
    private final CommunityMemoSourceFileSupport communityMemoSourceFileSupport;
    private final CommunityMemoValidationSupport communityMemoValidationSupport;
    private final CommunityMemoFailureEventSupport communityMemoFailureEventSupport;

    CommunityMemoSupport(CommunityMemoUserSupport communityMemoUserSupport,
        CommunityMemoSourceFileSupport communityMemoSourceFileSupport,
        CommunityMemoValidationSupport communityMemoValidationSupport,
        CommunityMemoFailureEventSupport communityMemoFailureEventSupport) {
        this.communityMemoUserSupport = communityMemoUserSupport;
        this.communityMemoSourceFileSupport = communityMemoSourceFileSupport;
        this.communityMemoValidationSupport = communityMemoValidationSupport;
        this.communityMemoFailureEventSupport = communityMemoFailureEventSupport;
    }

    UUID parseUserUuid(String userUuidValue) {
        return communityMemoUserSupport.parseUserUuid(userUuidValue);
    }

    void resolveUser(UUID userUuid) {
        communityMemoUserSupport.resolveUser(userUuid);
    }

    UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        return communityMemoUserSupport.parseOptionalViewerUuid(viewerUserUuidValue);
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName) {
        return communityMemoFailureEventSupport.findVisibleMemoOrLogNotFound(memoId, actorUuid, eventName);
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName, long startedAt) {
        return communityMemoFailureEventSupport.findVisibleMemoOrLogNotFound(memoId, actorUuid, eventName, startedAt);
    }

    void logCreateValidationFailed(String userUuidValue, CommunityMemoCreateRequest request, RuntimeException error,
        String moderationBlockedMessage, String moderationUnavailableMessage) {
        communityMemoFailureEventSupport.logCreateValidationFailed(userUuidValue, request, error,
            moderationBlockedMessage, moderationUnavailableMessage);
    }

    void logCommunityActionFailed(String eventName, String memoIdValue, String userUuidValue, RuntimeException error) {
        communityMemoFailureEventSupport.logCommunityActionFailed(eventName, memoIdValue, userUuidValue, error);
    }

    CommunityMemoReportReason validateReportReason(CommunityMemoReportRequest request) {
        return communityMemoValidationSupport.validateReportReason(request);
    }

    String normalizeReasonDetail(String reasonDetail) {
        return communityMemoValidationSupport.normalizeReasonDetail(reasonDetail);
    }

    CommunityMemoSourceType validateSourceType(CommunityMemoCreateRequest request) {
        return communityMemoSourceFileSupport.validateSourceType(request);
    }

    UUID resolveSourceArtifactId(CommunityMemoSourceType sourceType, String sourceGalleryIdValue, UUID userUuid) {
        return communityMemoSourceFileSupport.resolveSourceArtifactId(sourceType, sourceGalleryIdValue, userUuid);
    }

    UUID parseFileId(String fileIdValue, String invalidMessage) {
        return communityMemoSourceFileSupport.parseFileId(fileIdValue, invalidMessage);
    }

    void validateDifferentFiles(UUID originalFileId, UUID thumbnailFileId) {
        communityMemoSourceFileSupport.validateDifferentFiles(originalFileId, thumbnailFileId);
    }

    void validatePosition(CommunityMemoCreateRequest request) {
        communityMemoValidationSupport.validatePosition(request);
    }

    void validateLayout(CommunityMemoLayoutUpdateRequest request) {
        communityMemoValidationSupport.validateLayout(request);
    }

    void validateCommunityFile(FileUpload fileUpload, UUID userUuid) {
        communityMemoSourceFileSupport.validateCommunityFile(fileUpload, userUuid);
    }

    FileUpload findFileUpload(UUID fileId) {
        return communityMemoSourceFileSupport.findFileUpload(fileId);
    }

    String serializeDecoration(JsonNode decoration) {
        return communityMemoValidationSupport.serializeDecoration(decoration);
    }

    long calculateLatencyMs(long startedAt) {
        return communityMemoFailureEventSupport.calculateLatencyMs(startedAt);
    }

    boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return communityMemoUserSupport.isOwnedByViewer(userId, viewerUserUuid);
    }
}
