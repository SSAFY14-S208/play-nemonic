package com.nemonicworld.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoRow;
import com.nemonicworld.user.service.AnonymousUserResolver;
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
    private static final TypeReference<Map<String, Object>> DECORATION_TYPE = new TypeReference<>() {
    };

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoImageUrlResolver communityMemoImageUrlResolver;
    private final AnonymousUserResolver anonymousUserResolver;
    private final ObjectMapper objectMapper;

    public CommunityMemoServiceImpl(CommunityMemoRepository communityMemoRepository,
        CommunityMemoImageUrlResolver communityMemoImageUrlResolver, AnonymousUserResolver anonymousUserResolver,
        ObjectMapper objectMapper) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoImageUrlResolver = communityMemoImageUrlResolver;
        this.anonymousUserResolver = anonymousUserResolver;
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

    private UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    private CommunityMemoItemResponse toResponse(CommunityMemoRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoImageUrl = communityMemoImageUrlResolver.resolve(row.imageReference());
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);

        return new CommunityMemoItemResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            row.positionX(), row.positionY(), row.zIndex(), row.rotationDeg(), ownedByMe, row.attachedAt());
    }

    private CommunityMemoDetailResponse toDetailResponse(CommunityMemoDetailRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoImageUrl = communityMemoImageUrlResolver.resolve(row.imageReference());
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);
        String artifactId = row.artifactId() == null ? null : row.artifactId().toString();
        String galleryContentKind = row.artifactId() == null ? null : row.artifactKind();

        return new CommunityMemoDetailResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            row.positionX(), row.positionY(), row.zIndex(), row.rotationDeg(), ownedByMe, row.attachedAt(),
            parseDecoration(row.decoration()), artifactId, galleryContentKind, row.moderationStatus(),
            row.reportCount(), row.createdAt(), row.updatedAt());
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
            log.warn("커뮤니티 메모 decoration JSON을 파싱할 수 없습니다. decoration={}", decoration, e);

            return Map.of();
        }
    }
}
