package com.nemonicworld.artifact.service;

import com.nemonicworld.artifact.dto.response.ArtifactImageUrlResponse;
import com.nemonicworld.artifact.service.image.ArtifactImageUrlQueryUseCase;
import org.springframework.stereotype.Service;

@Service
public class ArtifactServiceImpl implements ArtifactService {

    private final ArtifactImageUrlQueryUseCase artifactImageUrlQueryUseCase;

    public ArtifactServiceImpl(ArtifactImageUrlQueryUseCase artifactImageUrlQueryUseCase) {
        this.artifactImageUrlQueryUseCase = artifactImageUrlQueryUseCase;
    }

    @Override
    public ArtifactImageUrlResponse getArtifactImageUrls(String userUuidValue, String artifactIdValue) {
        return artifactImageUrlQueryUseCase.getArtifactImageUrls(userUuidValue, artifactIdValue);
    }
}
