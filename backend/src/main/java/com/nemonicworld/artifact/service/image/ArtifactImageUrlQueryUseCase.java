package com.nemonicworld.artifact.service.image;

import com.nemonicworld.artifact.dto.response.ArtifactContentUrlResponse;
import com.nemonicworld.artifact.dto.response.ArtifactImageUrlResponse;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRepository;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ArtifactImageUrlQueryUseCase {

    private static final String INVALID_ARTIFACT_ID_MESSAGE = "유효하지 않은 산출물 ID 형식입니다.";
    private static final String ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE = "조회 가능한 산출물 이미지 URL을 찾을 수 없습니다.";

    private final ArtifactImageUrlRepository artifactImageUrlRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ArtifactContentUrlResolver artifactContentUrlResolver;

    public ArtifactImageUrlQueryUseCase(ArtifactImageUrlRepository artifactImageUrlRepository,
        AnonymousUserResolver anonymousUserResolver, MinioPublicUrlResolver minioPublicUrlResolver,
        ArtifactContentUrlResolver artifactContentUrlResolver) {
        this.artifactImageUrlRepository = artifactImageUrlRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.artifactContentUrlResolver = artifactContentUrlResolver;
    }

    @Transactional(readOnly = true)
    public ArtifactImageUrlResponse getArtifactImageUrls(String userUuidValue, String artifactIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID artifactId = parseArtifactId(artifactIdValue);

        anonymousUserResolver.resolve(userUuid);

        ArtifactImageUrlRow row = artifactImageUrlRepository.findActiveArtifactImageUrl(artifactId, userUuid)
            .orElseThrow(() -> new NotFoundException(ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE));

        String thumbnailUrl = minioPublicUrlResolver.resolve(row.thumbnailUrl());
        List<ArtifactContentUrlResponse> contents = artifactContentUrlResolver.createContents(row);
        if (!StringUtils.hasText(thumbnailUrl) && contents.isEmpty()) {
            throw new NotFoundException(ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE);
        }

        return new ArtifactImageUrlResponse(row.artifactId().toString(), row.kind(), thumbnailUrl, contents);
    }

    private UUID parseArtifactId(String artifactIdValue) {
        if (!StringUtils.hasText(artifactIdValue)) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }

        try {
            return UUID.fromString(artifactIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }
    }
}
