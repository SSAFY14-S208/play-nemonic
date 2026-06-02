package com.nemonicworld.artifact.service.download;

import org.springframework.stereotype.Service;

@Service
public class ArtifactQrAssetServiceImpl implements ArtifactQrAssetService {

    private final ArtifactQrAssetPrepareUseCase artifactQrAssetPrepareUseCase;

    public ArtifactQrAssetServiceImpl(ArtifactQrAssetPrepareUseCase artifactQrAssetPrepareUseCase) {
        this.artifactQrAssetPrepareUseCase = artifactQrAssetPrepareUseCase;
    }

    @Override
    public ArtifactQrAsset prepareQrAsset(String userUuidValue, String artifactIdValue) {
        return artifactQrAssetPrepareUseCase.prepareQrAsset(userUuidValue, artifactIdValue);
    }
}
