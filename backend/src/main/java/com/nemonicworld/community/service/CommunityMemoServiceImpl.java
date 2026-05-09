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
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.dto.response.CommunityMemoReportResponse;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoCreateCommand;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoRow;
import com.nemonicworld.community.repository.CommunityMemoSourceGalleryRow;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationException;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationRequest;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationResult;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 캔버스 공용 벽 메모 조회 유스케이스를 처리합니다.
 */
@Service
public class CommunityMemoServiceImpl implements CommunityMemoService {

    private static final Logger log = LoggerFactory.getLogger(CommunityMemoServiceImpl.class);
    private static final String COMMUNITY_MEMO_NOT_FOUND_MESSAGE = "존재하지 않는 커뮤니티 메모입니다.";
    private static final String UNSUPPORTED_SOURCE_TYPE_MESSAGE = "지원하지 않는 커뮤니티 메모 sourceType입니다.";
    private static final String INVALID_MEMO_SOURCE_MESSAGE = "커뮤니티 메모 원본 정보가 올바르지 않습니다.";
    private static final String INVALID_ORIGINAL_FILE_ID_MESSAGE = "유효하지 않은 originalFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String INVALID_SOURCE_GALLERY_ID_MESSAGE = "유효하지 않은 sourceGalleryId 형식입니다.";
    private static final String DUPLICATED_FILE_MESSAGE = "커뮤니티 메모 원본과 썸네일 파일은 서로 달라야 합니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String INVALID_POSITION_MESSAGE = "커뮤니티 메모 위치 정보가 올바르지 않습니다.";
    private static final String MEMO_ACCESS_DENIED_MESSAGE = "커뮤니티 메모 위치를 수정할 권한이 없습니다.";
    private static final String MEMO_DELETE_ACCESS_DENIED_MESSAGE = "커뮤니티 메모를 삭제할 권한이 없습니다.";
    private static final String INVALID_REPORT_REASON_MESSAGE = "커뮤니티 메모 신고 사유가 올바르지 않습니다.";
    private static final String OWN_MEMO_REPORT_MESSAGE = "본인 메모는 신고할 수 없습니다.";
    private static final String DUPLICATE_REPORT_MESSAGE = "이미 신고한 커뮤니티 메모입니다.";
    private static final String INVALID_DECORATION_MESSAGE = "커뮤니티 메모 데코레이션 정보가 올바르지 않습니다.";
    private static final String MODERATION_BLOCKED_MESSAGE = "부적절한 표현이 감지되어 게시할 수 없습니다.";
    private static final String MODERATION_UNAVAILABLE_MESSAGE = "커뮤니티 메모 모더레이션을 완료할 수 없습니다.";
    private static final String EMPTY_DECORATION_JSON = "{}";
    private static final int MAX_VISIBLE_MEMO_COUNT = 50;
    private static final int REPORT_HIDE_THRESHOLD = 5;
    private static final int MODERATION_LOG_TEXT_PREVIEW_LIMIT = 300;
    private static final TypeReference<Map<String, Object>> DECORATION_TYPE = new TypeReference<>() {
    };

    private final CommunityMemoRepository communityMemoRepository;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileUploadRepository fileUploadRepository;
    private final CommunityMemoModerationClient communityMemoModerationClient;
    private final ObjectMapper objectMapper;

    /**
     * 커뮤니티 메모 서비스가 사용하는 저장소, 파일, 모더레이션 의존성을 주입합니다.
     */
    public CommunityMemoServiceImpl(CommunityMemoRepository communityMemoRepository,
        MinioPublicUrlResolver minioPublicUrlResolver, AnonymousUserResolver anonymousUserResolver,
        FileUploadRepository fileUploadRepository, CommunityMemoModerationClient communityMemoModerationClient,
        ObjectMapper objectMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileUploadRepository = fileUploadRepository;
        this.communityMemoModerationClient = communityMemoModerationClient;
        this.objectMapper = objectMapper;
    }

    /**
     * visible 메모 목록을 조회하고, 선택 헤더 기준으로 ownedByMe와 이미지 URL을 계산합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue) {
        UUID viewerUserUuid = parseOptionalViewerUuid(viewerUserUuidValue);
        List<CommunityMemoItemResponse> items = communityMemoRepository.findVisibleMemos().stream()
            .map(row -> toResponse(row, viewerUserUuid)).toList();

        return new CommunityMemoListResponse(items, items.size());
    }

    /**
     * visible 메모 상세 정보를 조회하고, decoration과 대표 이미지 URL을 응답 DTO로 변환합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public CommunityMemoDetailResponse getCommunityMemo(String memoIdValue, String viewerUserUuidValue) {
        UUID memoId = anonymousUserResolver.parseUuid(memoIdValue);
        UUID viewerUserUuid = parseOptionalViewerUuid(viewerUserUuidValue);
        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));

        return toDetailResponse(row, viewerUserUuid);
    }

    /**
     * DIRECT/GALLERY 최종 스냅샷 파일을 검증하고, 게시 전 모더레이션 통과 후 메모를 생성합니다.
     */
    @Override
    @Transactional
    public CommunityMemoDetailResponse createCommunityMemo(String userUuidValue, CommunityMemoCreateRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);
        CommunityMemoSourceType sourceType = validateSourceType(request);
        // GALLERY 게시도 화면에 보여줄 이미지는 새 스냅샷을 쓰고, artifact는 원본 출처 추적에만 연결합니다.
        UUID sourceArtifactId = resolveSourceArtifactId(sourceType, request.sourceGalleryId(), userUuid);

        // 프론트가 최종 렌더링한 원본과 썸네일을 각각 업로드/confirm한 뒤에만 커뮤니티에 게시할 수 있습니다.
        UUID originalFileId = parseFileId(request.originalFileId(), INVALID_ORIGINAL_FILE_ID_MESSAGE);
        UUID thumbnailFileId = parseFileId(request.thumbnailFileId(), INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        validateDifferentFiles(originalFileId, thumbnailFileId);
        validatePosition(request);
        String decorationJson = serializeDecoration(request.decoration());
        FileUpload originalFileUpload = findFileUpload(originalFileId);
        FileUpload thumbnailFileUpload = findFileUpload(thumbnailFileId);
        validateCommunityFile(originalFileUpload, userUuid);
        validateCommunityFile(thumbnailFileUpload, userUuid);

        String originalImageUrl = minioPublicUrlResolver.resolve(originalFileUpload.getObjectKey());
        String thumbnailImageUrl = minioPublicUrlResolver.resolve(thumbnailFileUpload.getObjectKey());
        if (!StringUtils.hasText(originalImageUrl) || !StringUtils.hasText(thumbnailImageUrl)) {
            throw new BadRequestException(INVALID_MEMO_SOURCE_MESSAGE);
        }
        // 게시 전 모더레이션은 insert 이전에 끝내서 차단된 메모 row가 생기지 않도록 합니다.
        CommunityMemoModerationResult moderationResult = checkModeration(originalImageUrl, thumbnailImageUrl,
            normalizeClientText(request.clientText()), sourceType);

        LocalDateTime now = LocalDateTime.now();
        UUID memoId = UUID.randomUUID();
        CommunityMemoCreateCommand command = new CommunityMemoCreateCommand(memoId, userUuid, sourceArtifactId,
            originalFileUpload.getObjectKey(), thumbnailFileUpload.getObjectKey(), request.positionX(),
            request.positionY(), request.zIndex(), request.rotationDeg().floatValue(), decorationJson,
            moderationResult.ocrText(), serializeModerationCategories(moderationResult.categories()), now, now, now,
            now);
        communityMemoRepository.insertMemo(command);
        // FIFO는 생성 성공 직후에만 적용합니다. 위치 수정은 오래된 메모 정리에 영향을 주지 않습니다.
        expireOverflowVisibleMemos(memoId, now);

        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        return toDetailResponse(row, userUuid);
    }

    /**
     * 본인 visible 메모의 배치 정보만 갱신하고, 갱신된 상세 응답을 반환합니다.
     */
    @Override
    @Transactional
    public CommunityMemoDetailResponse updateCommunityMemoLayout(String memoIdValue, String userUuidValue,
        CommunityMemoLayoutUpdateRequest request) {
        UUID memoId = anonymousUserResolver.parseUuid(memoIdValue);
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);
        validateLayout(request);

        // 숨김/삭제 메모는 상세 조회와 동일하게 404로 낮추고, visible 메모에서만 소유자를 확인합니다.
        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (!isOwnedByViewer(row.userId(), userUuid)) {
            throw new ForbiddenException(MEMO_ACCESS_DENIED_MESSAGE);
        }

        // 위치 수정은 레이아웃 필드와 updated_at만 바꿉니다. 이미지, decoration, moderation, attached_at은
        // 유지합니다.
        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedCount = communityMemoRepository.updateMemoLayout(memoId, userUuid, request.positionX(),
            request.positionY(), request.zIndex(), request.rotationDeg().floatValue(), updatedAt);
        if (updatedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        CommunityMemoDetailRow updatedRow = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        return toDetailResponse(updatedRow, userUuid);
    }

    /**
     * 본인 visible 메모를 사용자 삭제 사유로 soft delete 처리합니다.
     */
    @Override
    @Transactional
    public void deleteCommunityMemo(String memoIdValue, String userUuidValue) {
        UUID memoId = anonymousUserResolver.parseUuid(memoIdValue);
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);

        // 삭제/숨김 메모는 상세 조회와 동일하게 404로 숨기고, visible 메모에서만 소유자를 확인합니다.
        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (!isOwnedByViewer(row.userId(), userUuid)) {
            throw new ForbiddenException(MEMO_DELETE_ACCESS_DENIED_MESSAGE);
        }

        // 삭제는 soft delete만 수행합니다. MinIO 파일, file_upload, artifact, gallery, moderation
        // 데이터는 보존합니다.
        LocalDateTime deletedAt = LocalDateTime.now();
        int deletedCount = communityMemoRepository.softDeleteMemo(memoId, userUuid, deletedAt);
        if (deletedCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }
    }

    /**
     * visible 메모를 신고하고, 누적 신고 수가 임계값에 도달하면 자동 숨김 처리합니다.
     */
    @Override
    @Transactional
    public CommunityMemoReportResponse reportCommunityMemo(String memoIdValue, String userUuidValue,
        CommunityMemoReportRequest request) {
        UUID memoId = anonymousUserResolver.parseUuid(memoIdValue);
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);
        CommunityMemoReportReason reason = validateReportReason(request);

        // 신고는 공용 벽에 노출 중인 메모만 받습니다. 숨김/삭제 메모는 다른 조회 API와 같이 404로 감춥니다.
        CommunityMemoDetailRow row = communityMemoRepository.findVisibleMemoById(memoId)
            .orElseThrow(() -> new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE));
        if (isOwnedByViewer(row.userId(), userUuid)) {
            throw new BadRequestException(OWN_MEMO_REPORT_MESSAGE);
        }

        if (communityMemoRepository.existsMemoReport(memoId, userUuid)) {
            throw new ConflictException(DUPLICATE_REPORT_MESSAGE);
        }

        LocalDateTime reportedAt = LocalDateTime.now();
        try {
            communityMemoRepository.insertMemoReport(memoId, userUuid, reason,
                normalizeReasonDetail(request.reasonDetail()), reportedAt);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_REPORT_MESSAGE);
        }

        int reportCount = communityMemoRepository.incrementReportCount(memoId);
        if (reportCount == 0) {
            throw new NotFoundException(COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        }

        boolean hidden = reportCount >= REPORT_HIDE_THRESHOLD;
        if (hidden) {
            communityMemoRepository.hideMemoByReportThreshold(memoId, reportedAt, REPORT_HIDE_THRESHOLD);
        }

        return new CommunityMemoReportResponse(memoId.toString(), reportCount, hidden);
    }

    /**
     * 조회용 선택 헤더 UUID를 파싱합니다. 헤더가 없으면 비로그인 감상자로 취급합니다.
     */
    private UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        // 커뮤니티 감상 조회는 사용자 존재 확인 없이 UUID 형식과 ownedByMe 계산에만 헤더를 사용합니다.
        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    /**
     * 신고 사유가 DB enum에 존재하는 소문자 값인지 검증합니다.
     */
    private CommunityMemoReportReason validateReportReason(CommunityMemoReportRequest request) {
        String reason = request == null ? null : request.reason();
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(INVALID_REPORT_REASON_MESSAGE);
        }

        return CommunityMemoReportReason.findByValue(reason)
            .orElseThrow(() -> new BadRequestException(INVALID_REPORT_REASON_MESSAGE));
    }

    /**
     * 신고 상세 사유는 선택 입력이므로 비어 있으면 DB null로 저장합니다.
     */
    private String normalizeReasonDetail(String reasonDetail) {
        return StringUtils.hasText(reasonDetail) ? reasonDetail.trim() : null;
    }

    /**
     * 생성 요청의 sourceType을 DIRECT/GALLERY 중 하나로 검증하고 enum으로 변환합니다.
     */
    private CommunityMemoSourceType validateSourceType(CommunityMemoCreateRequest request) {
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

        if (!StringUtils.hasText(sourceType)) {
            throw new BadRequestException(UNSUPPORTED_SOURCE_TYPE_MESSAGE);
        }

        throw new BadRequestException(UNSUPPORTED_SOURCE_TYPE_MESSAGE);
    }

    /**
     * GALLERY 생성 요청이면 소유한 활성 갤러리 항목을 찾아 출처 artifact ID를 반환합니다.
     */
    private UUID resolveSourceArtifactId(CommunityMemoSourceType sourceType, String sourceGalleryIdValue,
        UUID userUuid) {
        if (sourceType == CommunityMemoSourceType.DIRECT) {
            return null;
        }

        UUID sourceGalleryId = parseFileId(sourceGalleryIdValue, INVALID_SOURCE_GALLERY_ID_MESSAGE);
        CommunityMemoSourceGalleryRow sourceGallery = communityMemoRepository
            .findActiveSourceGallery(sourceGalleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));

        return sourceGallery.artifactId();
    }

    /**
     * 요청 문자열을 UUID로 변환하고, 누락/형식 오류는 호출자가 넘긴 메시지로 400 처리합니다.
     */
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

    /**
     * 원본 이미지 파일과 썸네일 이미지 파일이 같은 업로드 파일인지 검증합니다.
     */
    private void validateDifferentFiles(UUID originalFileId, UUID thumbnailFileId) {
        if (originalFileId.equals(thumbnailFileId)) {
            throw new BadRequestException(DUPLICATED_FILE_MESSAGE);
        }
    }

    /**
     * 생성 요청의 좌표, z-index, 회전값이 필수이며 유한한 숫자인지 확인합니다.
     */
    private void validatePosition(CommunityMemoCreateRequest request) {
        // 좌표 범위는 프론트 캔버스 정책을 신뢰하되, DB/JSON에서 깨지는 비정상 숫자만 차단합니다.
        if (request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(INVALID_POSITION_MESSAGE);
        }
    }

    /**
     * 위치 수정 요청의 좌표, z-index, 회전값이 필수이며 유한한 숫자인지 확인합니다.
     */
    private void validateLayout(CommunityMemoLayoutUpdateRequest request) {
        // rotation_deg는 REAL 컬럼이라 Double 입력이 float로 안전하게 내려갈 수 있는지도 함께 확인합니다.
        if (request == null || request.positionX() == null || request.positionY() == null || request.zIndex() == null
            || request.rotationDeg() == null || !Double.isFinite(request.positionX())
            || !Double.isFinite(request.positionY()) || !Double.isFinite(request.rotationDeg())
            || Math.abs(request.rotationDeg()) > Float.MAX_VALUE) {
            throw new BadRequestException(INVALID_POSITION_MESSAGE);
        }
    }

    /**
     * 커뮤니티 게시에 사용할 파일이 요청자 소유의 업로드 완료 COMMUNITY 파일인지 검증합니다.
     */
    private void validateCommunityFile(FileUpload fileUpload, UUID userUuid) {
        // 커뮤니티 게시 이미지는 반드시 요청자 본인의 COMMUNITY 목적 업로드 완료 파일이어야 합니다.
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

    /**
     * 파일 업로드 row를 조회하고 없으면 공통 404 예외로 변환합니다.
     */
    private FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    /**
     * decoration JSON object를 DB 저장용 문자열로 직렬화하고, 없으면 빈 객체를 저장합니다.
     */
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

    /**
     * 생성 직후 visible 메모가 50개를 초과하면 새 메모를 제외한 오래된 메모를 만료 처리합니다.
     */
    private void expireOverflowVisibleMemos(UUID newMemoId, LocalDateTime now) {
        int overflowCount = communityMemoRepository.countVisibleMemos() - MAX_VISIBLE_MEMO_COUNT;
        if (overflowCount > 0) {
            // 방금 붙인 메모는 제외하고, 노출 중인 오래된 메모부터 expired soft delete 처리합니다.
            communityMemoRepository.expireOldestVisibleMemos(newMemoId, now, overflowCount);
        }
    }

    /**
     * FastAPI 모더레이션을 호출하고, 차단/장애 결과를 커뮤니티 생성 예외로 변환합니다.
     */
    private CommunityMemoModerationResult checkModeration(String originalImageUrl, String thumbnailImageUrl,
        String clientText, CommunityMemoSourceType sourceType) {
        try {
            CommunityMemoModerationResult result = communityMemoModerationClient
                .check(new CommunityMemoModerationRequest(originalImageUrl, thumbnailImageUrl, clientText,
                    sourceType.value()));
            logModerationResult(result, clientText);
            if (!result.allowed()) {
                throw new BadRequestException(MODERATION_BLOCKED_MESSAGE);
            }

            return result;
        } catch (CommunityMemoModerationException e) {
            throw new BadRequestException(MODERATION_UNAVAILABLE_MESSAGE);
        }
    }

    /**
     * 모더레이션 요청에 전달할 클라이언트 텍스트 null 값을 빈 문자열로 정규화합니다.
     */
    private String normalizeClientText(String clientText) {
        return clientText == null ? "" : clientText;
    }

    /**
     * 모더레이션 결과를 운영 로그에 남기되, 긴 텍스트는 미리보기 길이로 제한합니다.
     */
    private void logModerationResult(CommunityMemoModerationResult result, String clientText) {
        String categories = result.categories() == null || result.categories().isNull()
            ? "[]"
            : result.categories().toString();
        log.info("커뮤니티 메모 모더레이션 결과 allowed={} clientTextPreview={} checkedTextPreview={} categories={}",
            result.allowed(), previewModerationText(clientText), previewModerationText(result.ocrText()), categories);
    }

    /**
     * 모더레이션 로그에 남길 텍스트를 공백 정리 후 제한 길이로 축약합니다.
     */
    private String previewModerationText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        // OCR/텍스트박스 원문은 길 수 있으므로 운영 로그에는 한 줄 미리보기만 남깁니다.
        String compactText = text.replaceAll("\\s+", " ").trim();
        if (compactText.length() <= MODERATION_LOG_TEXT_PREVIEW_LIMIT) {
            return compactText;
        }

        return compactText.substring(0, MODERATION_LOG_TEXT_PREVIEW_LIMIT) + "...";
    }

    /**
     * 모더레이션 카테고리 JSON을 DB 저장용 문자열로 변환합니다.
     */
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

    /**
     * 목록 조회 row를 목록 응답 item DTO로 변환합니다.
     */
    private CommunityMemoItemResponse toResponse(CommunityMemoRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        // 목록도 artifact 원본이 아니라 community_memo에 저장된 최종 게시 스냅샷 URL만 내려줍니다.
        String memoOriginalImageUrl = minioPublicUrlResolver.resolve(row.originalImageReference());
        String memoThumbnailImageUrl = minioPublicUrlResolver.resolve(row.thumbnailImageReference());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);

        return new CommunityMemoItemResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, row.positionX(), row.positionY(), row.zIndex(),
            row.rotationDeg(), ownedByMe, row.attachedAt());
    }

    /**
     * 상세 조회 row를 상세/생성 응답 DTO로 변환합니다.
     */
    private CommunityMemoDetailResponse toDetailResponse(CommunityMemoDetailRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        // 상세와 생성 응답도 목록과 같은 대표 이미지 fallback 정책을 공유합니다.
        String memoOriginalImageUrl = minioPublicUrlResolver.resolve(row.originalImageReference());
        String memoThumbnailImageUrl = minioPublicUrlResolver.resolve(row.thumbnailImageReference());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);
        String artifactId = row.artifactId() == null ? null : row.artifactId().toString();
        String galleryContentKind = row.artifactId() == null ? null : row.artifactKind();

        return new CommunityMemoDetailResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, row.positionX(), row.positionY(), row.zIndex(),
            row.rotationDeg(), ownedByMe, row.attachedAt(), parseDecoration(row.decoration()), artifactId,
            galleryContentKind, row.moderationStatus(), row.reportCount(), row.createdAt(), row.updatedAt());
    }

    /**
     * 호환 대표 이미지 URL을 썸네일 우선, 원본 fallback 순서로 선택합니다.
     */
    private String representativeImageUrl(String memoOriginalImageUrl, String memoThumbnailImageUrl) {
        return memoThumbnailImageUrl == null ? memoOriginalImageUrl : memoThumbnailImageUrl;
    }

    /**
     * artifact 연결 여부로 DIRECT/GALLERY 응답 sourceType을 계산합니다.
     */
    private String resolveSourceType(UUID artifactId) {
        return artifactId == null ? CommunityMemoSourceType.DIRECT.value() : CommunityMemoSourceType.GALLERY.value();
    }

    /**
     * viewer UUID와 메모 작성자 UUID가 같은지 비교해 ownedByMe 값을 계산합니다.
     */
    private boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return viewerUserUuid != null && viewerUserUuid.equals(userId);
    }

    /**
     * DB에 저장된 decoration 문자열을 응답용 객체로 파싱하고, 비정상 값은 빈 객체로 보정합니다.
     */
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
