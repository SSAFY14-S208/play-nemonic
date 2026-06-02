package com.nemonicworld.artifact.service.download;

import org.springframework.stereotype.Service;

@Service
public class ArtifactDownloadPrepareUseCase {

    private final ArtifactQrAssetService artifactQrAssetService;
    private final ArtifactDownloadStorage artifactDownloadStorage;
    private final ArtifactDownloadEventLogger artifactDownloadEventLogger;

    public ArtifactDownloadPrepareUseCase(ArtifactQrAssetService artifactQrAssetService,
        ArtifactDownloadStorage artifactDownloadStorage, ArtifactDownloadEventLogger artifactDownloadEventLogger) {
        this.artifactQrAssetService = artifactQrAssetService;
        this.artifactDownloadStorage = artifactDownloadStorage;
        this.artifactDownloadEventLogger = artifactDownloadEventLogger;
    }

    public ArtifactDownloadFile prepareDownloadFile(String userUuidValue, String artifactIdValue) {
        long startedAt = artifactDownloadEventLogger.startTimer();
        artifactDownloadEventLogger.logRequested(userUuidValue, artifactIdValue);
        try {
            ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(userUuidValue, artifactIdValue);
            byte[] bytes = artifactDownloadStorage.download(asset.cacheObjectKey());
            artifactDownloadEventLogger.logCreated(userUuidValue, asset, bytes.length, startedAt);

            return new ArtifactDownloadFile(bytes, asset.fileName(), asset.contentType());
        } catch (RuntimeException e) {
            artifactDownloadEventLogger.logFailed(userUuidValue, artifactIdValue, e, startedAt);
            throw e;
        }
    }
}
