package com.nemonicworld.community.service;

import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoRow;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 캔버스 공용 벽 메모 조회 유스케이스를 처리합니다.
 */
@Service
public class CommunityMemoServiceImpl implements CommunityMemoService {

    private static final String DIRECT_SOURCE_TYPE = "DIRECT";
    private static final String GALLERY_SOURCE_TYPE = "GALLERY";

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoImageUrlResolver communityMemoImageUrlResolver;
    private final AnonymousUserResolver anonymousUserResolver;

    public CommunityMemoServiceImpl(CommunityMemoRepository communityMemoRepository,
        CommunityMemoImageUrlResolver communityMemoImageUrlResolver, AnonymousUserResolver anonymousUserResolver) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoImageUrlResolver = communityMemoImageUrlResolver;
        this.anonymousUserResolver = anonymousUserResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMemoListResponse getCommunityMemos(String viewerUserUuidValue) {
        UUID viewerUserUuid = parseOptionalViewerUuid(viewerUserUuidValue);
        List<CommunityMemoItemResponse> items = communityMemoRepository.findVisibleMemos().stream()
            .map(row -> toResponse(row, viewerUserUuid)).toList();

        return new CommunityMemoListResponse(items, items.size());
    }

    private UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    private CommunityMemoItemResponse toResponse(CommunityMemoRow row, UUID viewerUserUuid) {
        String sourceType = row.artifactId() == null ? DIRECT_SOURCE_TYPE : GALLERY_SOURCE_TYPE;
        String memoImageUrl = communityMemoImageUrlResolver.resolve(row.imageReference());
        boolean ownedByMe = viewerUserUuid != null && viewerUserUuid.equals(row.userId());

        return new CommunityMemoItemResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            row.positionX(), row.positionY(), row.zIndex(), row.rotationDeg(), ownedByMe, row.attachedAt());
    }
}
