package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareEventLogger;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class CommunityMemoShareUseCase {

    private static final String COMMUNITY_MEMO_SHARE_NOT_FOUND_EVENT = "community_memo_share_not_found";

    private final CommunityMemoSupport communityMemoSupport;
    private final CommunityMemoShareSourceResolver communityMemoShareSourceResolver;
    private final CommunityMemoShareQrCacheSupport communityMemoShareQrCacheSupport;
    private final CommunityMemoShareUrlSupport communityMemoShareUrlSupport;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ShareEventLogger shareEventLogger;

    CommunityMemoShareUseCase(CommunityMemoSupport communityMemoSupport,
        CommunityMemoShareSourceResolver communityMemoShareSourceResolver,
        CommunityMemoShareQrCacheSupport communityMemoShareQrCacheSupport,
        CommunityMemoShareUrlSupport communityMemoShareUrlSupport, MinioPublicUrlResolver minioPublicUrlResolver,
        ShareEventLogger shareEventLogger) {
        this.communityMemoSupport = communityMemoSupport;
        this.communityMemoShareSourceResolver = communityMemoShareSourceResolver;
        this.communityMemoShareQrCacheSupport = communityMemoShareQrCacheSupport;
        this.communityMemoShareUrlSupport = communityMemoShareUrlSupport;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.shareEventLogger = shareEventLogger;
    }

    @Transactional
    ShareCreateResponse createCommunityMemoShare(String memoIdValue, String userUuidValue) {
        try {
            return createCommunityMemoShareInternal(memoIdValue, userUuidValue);
        } catch (RuntimeException e) {
            shareEventLogger.logCommunityMemoShareCreateFailed(userUuidValue, memoIdValue, e);
            throw e;
        }
    }

    private ShareCreateResponse createCommunityMemoShareInternal(String memoIdValue, String userUuidValue) {
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            COMMUNITY_MEMO_SHARE_NOT_FOUND_EVENT);
        CommunityMemoShareSourceResolver.ShareSource shareSource = communityMemoShareSourceResolver.resolve(row);
        CommunityMemoShareUrlSupport.ShareUrls shareUrls = communityMemoShareUrlSupport.createShareUrls(memoId);
        String cacheObjectKey = communityMemoShareQrCacheSupport.prepareQrCacheObject(memoId, shareSource,
            shareUrls.qrUrl());

        String imageUrl = minioPublicUrlResolver.resolve(cacheObjectKey);
        if (!StringUtils.hasText(imageUrl)) {
            throw new BadRequestException(CommunityMemoShareSourceResolver.IMAGE_REQUIRED_MESSAGE);
        }

        shareEventLogger.logCommunityMemoShareCreated(userUuidValue, shareUrls.shareToken(), memoId,
            CommunityMemoShareUrlSupport.COMMUNITY_MEMO_RESULT_CAMPAIGN);

        return new ShareCreateResponse(shareUrls.shareToken(), imageUrl, shareUrls.siteUrl(), shareUrls.kakaoUrl(),
            shareUrls.instagramUrl());
    }
}
