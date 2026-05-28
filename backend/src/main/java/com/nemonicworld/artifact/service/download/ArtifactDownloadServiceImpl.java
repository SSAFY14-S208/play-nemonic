package com.nemonicworld.artifact.service.download;

import org.springframework.stereotype.Service;

@Service
public class ArtifactDownloadServiceImpl implements ArtifactDownloadService {

    private final ArtifactDownloadPrepareUseCase artifactDownloadPrepareUseCase;

    public ArtifactDownloadServiceImpl(ArtifactDownloadPrepareUseCase artifactDownloadPrepareUseCase) {
        this.artifactDownloadPrepareUseCase = artifactDownloadPrepareUseCase;
    }

    @Override
    public ArtifactDownloadFile prepareDownloadFile(String userUuidValue, String artifactIdValue) {
        return artifactDownloadPrepareUseCase.prepareDownloadFile(userUuidValue, artifactIdValue);
    }
}
