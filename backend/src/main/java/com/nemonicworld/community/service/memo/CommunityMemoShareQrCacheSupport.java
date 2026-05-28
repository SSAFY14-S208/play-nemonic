package com.nemonicworld.community.service.memo;

import com.nemonicworld.artifact.service.download.ArtifactDownloadStorage;
import com.nemonicworld.artifact.service.download.ArtifactQrComposer;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CommunityMemoShareQrCacheSupport {

    private static final String QR_CACHE_FILE_STEM = "result-qr-v3";

    private final ArtifactDownloadStorage artifactDownloadStorage;
    private final ArtifactQrComposer artifactQrComposer;

    CommunityMemoShareQrCacheSupport(ArtifactDownloadStorage artifactDownloadStorage,
        ArtifactQrComposer artifactQrComposer) {
        this.artifactDownloadStorage = artifactDownloadStorage;
        this.artifactQrComposer = artifactQrComposer;
    }

    String prepareQrCacheObject(UUID memoId, CommunityMemoShareSourceResolver.ShareSource shareSource, String qrUrl) {
        String cacheObjectKey = cacheObjectKey(memoId, shareSource.extension());
        if (artifactDownloadStorage.exists(cacheObjectKey)) {
            return cacheObjectKey;
        }

        byte[] sourceBytes = artifactDownloadStorage.download(shareSource.objectKey());
        byte[] composedBytes = artifactQrComposer.compose(shareSource.sourceContentType(), sourceBytes, qrUrl);
        artifactDownloadStorage.upload(cacheObjectKey, composedBytes, shareSource.resultContentType());

        return cacheObjectKey;
    }

    private String cacheObjectKey(UUID memoId, String extension) {
        return "community-memo-shares/%s/%s.%s".formatted(memoId, QR_CACHE_FILE_STEM, extension);
    }
}
