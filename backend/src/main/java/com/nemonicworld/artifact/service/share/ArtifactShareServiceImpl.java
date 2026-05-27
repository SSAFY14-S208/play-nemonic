package com.nemonicworld.artifact.service.share;

import com.nemonicworld.share.dto.response.ShareCreateResponse;
import org.springframework.stereotype.Service;

@Service
public class ArtifactShareServiceImpl implements ArtifactShareService {

    private final ArtifactShareCreateUseCase artifactShareCreateUseCase;

    public ArtifactShareServiceImpl(ArtifactShareCreateUseCase artifactShareCreateUseCase) {
        this.artifactShareCreateUseCase = artifactShareCreateUseCase;
    }

    @Override
    public ShareCreateResponse createArtifactShare(String userUuidValue, String artifactIdValue) {
        return artifactShareCreateUseCase.createArtifactShare(userUuidValue, artifactIdValue);
    }
}
