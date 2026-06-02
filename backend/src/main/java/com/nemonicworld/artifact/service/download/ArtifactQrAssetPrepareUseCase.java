package com.nemonicworld.artifact.service.download;

import com.nemonicworld.artifact.repository.ArtifactImageUrlRepository;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ArtifactQrAssetPrepareUseCase {

    private static final String INVALID_ARTIFACT_ID_MESSAGE = "유효하지 않은 산출물 ID 형식입니다.";
    private static final String ARTIFACT_DOWNLOAD_NOT_FOUND_MESSAGE = "다운로드 가능한 산출물을 찾을 수 없습니다.";
    private static final String QR_CACHE_FILE_STEM = "result-qr-v4";

    private final ArtifactImageUrlRepository artifactImageUrlRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final ArtifactDownloadStorage artifactDownloadStorage;
    private final ArtifactQrComposer artifactQrComposer;
    private final ArtifactDownloadTargetResolver artifactDownloadTargetResolver;
    private final ArtifactQrShareUrlSupport artifactQrShareUrlSupport;

    public ArtifactQrAssetPrepareUseCase(ArtifactImageUrlRepository artifactImageUrlRepository,
        AnonymousUserResolver anonymousUserResolver, ArtifactDownloadStorage artifactDownloadStorage,
        ArtifactQrComposer artifactQrComposer, ArtifactDownloadTargetResolver artifactDownloadTargetResolver,
        ArtifactQrShareUrlSupport artifactQrShareUrlSupport) {
        this.artifactImageUrlRepository = artifactImageUrlRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.artifactDownloadStorage = artifactDownloadStorage;
        this.artifactQrComposer = artifactQrComposer;
        this.artifactDownloadTargetResolver = artifactDownloadTargetResolver;
        this.artifactQrShareUrlSupport = artifactQrShareUrlSupport;
    }

    @Transactional
    public ArtifactQrAsset prepareQrAsset(String userUuidValue, String artifactIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID artifactId = parseArtifactId(artifactIdValue);

        anonymousUserResolver.resolve(userUuid);

        ArtifactImageUrlRow row = artifactImageUrlRepository.findActiveArtifactImageUrl(artifactId, userUuid)
            .orElseThrow(() -> new NotFoundException(ARTIFACT_DOWNLOAD_NOT_FOUND_MESSAGE));
        ArtifactDownloadTarget target = artifactDownloadTargetResolver.resolve(row);
        String shareToken = artifactQrShareUrlSupport.issueShareToken(row.artifactId(), row.kind());
        String cacheObjectKey = cacheObjectKey(artifactId, target.extension());
        String fileName = fileName(artifactId, target.extension());

        if (!artifactDownloadStorage.exists(cacheObjectKey)) {
            byte[] sourceBytes = artifactDownloadStorage.download(target.objectKey().trim());
            byte[] composedBytes = artifactQrComposer.compose(target.sourceContentType(), sourceBytes,
                artifactQrShareUrlSupport.qrUrl(shareToken));
            artifactDownloadStorage.upload(cacheObjectKey, composedBytes, target.resultContentType());
        }

        return new ArtifactQrAsset(row.artifactId(), row.kind(), cacheObjectKey, fileName, target.resultContentType(),
            shareToken);
    }

    private String cacheObjectKey(UUID artifactId, String extension) {
        return "artifact-downloads/%s/%s.%s".formatted(artifactId, QR_CACHE_FILE_STEM, extension);
    }

    private String fileName(UUID artifactId, String extension) {
        return "nemonic-%s.%s".formatted(artifactId, extension);
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
