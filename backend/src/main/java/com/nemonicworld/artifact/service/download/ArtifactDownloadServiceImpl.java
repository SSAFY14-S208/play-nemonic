package com.nemonicworld.artifact.service.download;

import org.springframework.stereotype.Service;

@Service
public class ArtifactDownloadServiceImpl implements ArtifactDownloadService {

    private final ArtifactQrAssetService artifactQrAssetService;
    private final ArtifactDownloadStorage artifactDownloadStorage;

    public ArtifactDownloadServiceImpl(ArtifactQrAssetService artifactQrAssetService,
        ArtifactDownloadStorage artifactDownloadStorage) {
        this.artifactQrAssetService = artifactQrAssetService;
        this.artifactDownloadStorage = artifactDownloadStorage;
    }

    @Override
    public ArtifactDownloadFile prepareDownloadFile(String userUuidValue, String artifactIdValue) {
        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(userUuidValue, artifactIdValue);

        return new ArtifactDownloadFile(artifactDownloadStorage.download(asset.cacheObjectKey()), asset.fileName(),
            asset.contentType());
    }
}
