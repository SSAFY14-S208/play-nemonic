package com.nemonicworld.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.repository.CommunityMemoCreateCommand;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoRow;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationException;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationRequest;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationResult;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 캔버스 공용 벽 메모 조회 유스케이스를 처리합니다.
 */
@Service
public class CommunityMemoServiceImpl implements CommunityMemoService {

    private static final Logger log = LoggerFactory.getLogger(CommunityMemoServiceImpl.class);
    private static final String DIRECT_SOURCE_TYPE = "DIRECT";
    private static final String GALLERY_SOURCE_TYPE = "GALLERY";
    private static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    private static final String UNSUPPORTED_SOURCE_TYPE_MESSAGE = "지원하지 않는 커뮤니티 메모 sourceType입니다.";
    private static final String INVALID_MEMO_SOURCE_MESSAGE = "커뮤니티 메모 원본 정보가 올바르지 않습니다.";
    private static final String INVALID_ORIGINAL_FILE_ID_MESSAGE = "유효하지 않은 originalFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String DUPLICATED_FILE_MESSAGE = "커뮤니티 메모 원본과 썸네일 파일은 서로 달라야 합니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String INVALID_POSITION_MESSAGE = "커뮤니티 메모 위치 정보가 올바르지 않습니다.";
    private static final String INVALID_DECORATION_MESSAGE = "커뮤니티 메모 데코레이션 정보가 올바르지 않습니다.";
    private static final String MODERATION_BLOCKED_MESSAGE = "부적절한 표현이 감지되어 게시할 수 없습니다.";
    private static final String MODERATION_UNAVAILABLE_MESSAGE = "커뮤니티 메모 모더레이션을 완료할 수 없습니다.";
    private static final String EMPTY_DECORATION_JSON = "{}";
    private static final TypeReference<Map<String, Object>> DECORATION_TYPE = new TypeReference<>() {
    };

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoImageUrlResolver communityMemoImageUrlResolver;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileUploadRepository fileUploadRepository;
    private final CommunityMemoModerationClient communityMemoModerationClient;
    private final ObjectMapper objectMapper;

    public CommunityMemoServiceImpl(CommunityMemoRepository communityMemoRepository,
        CommunityMemoImageUrlResolver communityMemoImageUrlResolver, AnonymousUserResolver anonymousUserResolver,
        FileUploadRepository fileUploadRepository, CommunityMemoModerationClient communityMemoModerationClient,
        ObjectMapper objectMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoImageUrlResolver = communityMemoImageUrlResolver;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileUploadRepository = fileUploadRepository;
        this.communityMemoModerationClient = communityMemoModerationClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue) {
        UUID viewerUserUuid = parseOptionalViewerUuid(viewerUserUuidValue);
        List<CommunityMemoItemResponse> items = communityMemoRepository.findVisibleMemos().stream()
            .map(row -> toResponse(row, viewerUserUuid)).toList();

        return new CommunityMemoListResponse(items, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMemoDetailResponse getCommunityMemo(String memoIdValue, String viewerUserUuidValue) {
        UUID memoId = anonymousUserResolver.parseUuid(memoIdValue);
        UUID viewerUserUuid = parseOptionalViewerUuid(viewerUserUuidValue);
        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));

        return toDetailResponse(row, viewerUserUuid);
    }

    @Override
    @Transactional
    public CommunityMemoDetailResponse createCommunityMemo(String userUuidValue, CommunityMemoCreateRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);
        validateDirectSourceType(request);

        UUID originalFileId = parseFileId(request.originalFileId(), INVALID_ORIGINAL_FILE_ID_MESSAGE);
        UUID thumbnailFileId = parseFileId(request.thumbnailFileId(), INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        validateDifferentFiles(originalFileId, thumbnailFileId);
        validatePosition(request);
        String decorationJson = serializeDecoration(request.decoration());
        FileUpload originalFileUpload = findFileUpload(originalFileId);
        FileUpload thumbnailFileUpload = findFileUpload(thumbnailFileId);
        validateDirectFile(originalFileUpload, userUuid);
        validateDirectFile(thumbnailFileUpload, userUuid);

        String originalImageUrl = communityMemoImageUrlResolver.resolve(originalFileUpload.getObjectKey());
        if (!StringUtils.hasText(originalImageUrl)) {
            throw new BadRequestException(INVALID_MEMO_SOURCE_MESSAGE);
        }
        CommunityMemoModerationResult moderationResult = checkModeration(originalImageUrl,
            normalizeClientText(request.clientText()));

        LocalDateTime now = LocalDateTime.now();
        UUID memoId = UUID.randomUUID();
        CommunityMemoCreateCommand command = new CommunityMemoCreateCommand(memoId, userUuid,
            originalFileUpload.getObjectKey(), thumbnailFileUpload.getObjectKey(), request.positionX(),
            request.positionY(), request.zIndex(), request.rotationDeg().floatValue(), decorationJson,
            moderationResult.ocrText(), serializeModerationCategories(moderationResult.categories()), now, now, now,
            now);
        communityMemoRepository.insertDirectMemo(command);

        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        return toDetailResponse(row, userUuid);
    }

    private UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        // 커뮤니티 감상 조회는 사용자 존재 확인 없이 UUID 형식과 ownedByMe 계산에만 헤더를 사용합니다.
        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    private void validateDirectSourceType(CommunityMemoCreateRequest request) {
        String sourceType = request == null ? null : request.sourceType();
        if (!DIRECT_SOURCE_TYPE.equals(sourceType)) {
            throw new BadRequestException(UNSUPPORTED_SOURCE_TYPE_MESSAGE);
        }

        if (StringUtils.hasText(request.sourceGalleryId())) {
            throw new BadRequestException(INVALID_MEMO_SOURCE_MESSAGE);
        }
    }

    private UUID parseFileId(String fileIdValue, String invalidMessage) {
        if (!StringUtils.hasText(fileIdValue)) {
            throw new BadRequestException(invalidMessage);
        }

        try {
            return UUID.fromString(fileIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(invalidMessage);
        }
    }

    private void validateDifferentFiles(UUID originalFileId, UUID thumbnailFileId) {
        if (originalFileId.equals(thumbnailFileId)) {
            throw new BadRequestException(DUPLICATED_FILE_MESSAGE);
        }
    }

    private void validatePosition(CommunityMemoCreateRequest request) {
        if (request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(INVALID_POSITION_MESSAGE);
        }
    }

    private void validateDirectFile(FileUpload fileUpload, UUID userUuid) {
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

    private FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    private String serializeDecoration(JsonNode decoration) {
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

    private CommunityMemoModerationResult checkModeration(String originalImageUrl, String clientText) {
        try {
            CommunityMemoModerationResult result = communityMemoModerationClient
                .check(new CommunityMemoModerationRequest(originalImageUrl, clientText, DIRECT_SOURCE_TYPE));
            if (!result.allowed()) {
                throw new BadRequestException(MODERATION_BLOCKED_MESSAGE);
            }

            return result;
        } catch (CommunityMemoModerationException e) {
            throw new BadRequestException(MODERATION_UNAVAILABLE_MESSAGE);
        }
    }

    private String normalizeClientText(String clientText) {
        return clientText == null ? "" : clientText;
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

    private CommunityMemoItemResponse toResponse(CommunityMemoRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoOriginalImageUrl = communityMemoImageUrlResolver.resolve(row.originalImageReference());
        String memoThumbnailImageUrl = communityMemoImageUrlResolver.resolve(row.thumbnailImageReference());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);

        return new CommunityMemoItemResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, row.positionX(), row.positionY(), row.zIndex(),
            row.rotationDeg(), ownedByMe, row.attachedAt());
    }

    private CommunityMemoDetailResponse toDetailResponse(CommunityMemoDetailRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoOriginalImageUrl = communityMemoImageUrlResolver.resolve(row.originalImageReference());
        String memoThumbnailImageUrl = communityMemoImageUrlResolver.resolve(row.thumbnailImageReference());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);
        String artifactId = row.artifactId() == null ? null : row.artifactId().toString();
        String galleryContentKind = row.artifactId() == null ? null : row.artifactKind();

        return new CommunityMemoDetailResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, row.positionX(), row.positionY(), row.zIndex(),
            row.rotationDeg(), ownedByMe, row.attachedAt(), parseDecoration(row.decoration()), artifactId,
            galleryContentKind, row.moderationStatus(), row.reportCount(), row.createdAt(), row.updatedAt());
    }

    private String representativeImageUrl(String memoOriginalImageUrl, String memoThumbnailImageUrl) {
        return memoThumbnailImageUrl == null ? memoOriginalImageUrl : memoThumbnailImageUrl;
    }

    private String resolveSourceType(UUID artifactId) {
        return artifactId == null ? DIRECT_SOURCE_TYPE : GALLERY_SOURCE_TYPE;
    }

    private boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return viewerUserUuid != null && viewerUserUuid.equals(userId);
    }

    private Map<String, Object> parseDecoration(String decoration) {
        if (!StringUtils.hasText(decoration)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedDecoration = objectMapper.readValue(decoration, DECORATION_TYPE);
            return parsedDecoration == null ? Map.of() : parsedDecoration;
        } catch (JsonProcessingException e) {
            // 깨진 decoration 데이터가 있어도 상세 패널 조회는 실패시키지 않고 빈 객체로 낮춥니다.
            log.warn("커뮤니티 메모 decoration JSON을 파싱할 수 없습니다. decoration={}", decoration, e);

            return Map.of();
        }
    }
}
