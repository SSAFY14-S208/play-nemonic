package com.nemonicworld.artifact.service;

import com.nemonicworld.artifact.dto.response.ArtifactImageUrlResponse;

public interface ArtifactService {

    /**
     * 요청 사용자가 보관 중인 산출물의 썸네일과 콘텐츠 URL을 조회합니다.
     */
    ArtifactImageUrlResponse getArtifactImageUrls(String userUuidValue, String artifactIdValue);
}
