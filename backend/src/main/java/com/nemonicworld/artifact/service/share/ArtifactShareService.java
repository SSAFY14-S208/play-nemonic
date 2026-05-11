package com.nemonicworld.artifact.service.share;

import com.nemonicworld.share.dto.response.ShareCreateResponse;

public interface ArtifactShareService {

    ShareCreateResponse createArtifactShare(String userUuidValue, String artifactIdValue);
}
