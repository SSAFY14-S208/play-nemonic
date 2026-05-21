package com.nemonicworld.artifact.service.download;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.UUID;
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
        long startedAt = System.nanoTime();
        String userUuid = safeUuid(userUuidValue);
        StructuredEventLogger.apiBusiness("artifact_download_requested", "artifact", userUuid,
            StructuredEventLogger.metadata("artifact_id", artifactIdValue, "result", "requested"));
        try {
            ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(userUuidValue, artifactIdValue);
            byte[] bytes = artifactDownloadStorage.download(asset.cacheObjectKey());
            StructuredEventLogger.apiBusiness("artifact_download_created", asset.kind(), userUuid,
                StructuredEventLogger.metadata("artifact_id", asset.artifactId(), "file_name", asset.fileName(),
                    "content_type", asset.contentType(), "byte_size", bytes.length, "duration_ms",
                    calculateDurationMs(startedAt), "result", "success"));

            return new ArtifactDownloadFile(bytes, asset.fileName(), asset.contentType());
        } catch (RuntimeException e) {
            StructuredEventLogger.apiBusinessWarn("artifact_download_failed", "artifact", userUuid,
                "artifact download failed",
                StructuredEventLogger.metadata("artifact_id", artifactIdValue, "duration_ms",
                    calculateDurationMs(startedAt), "result", "failed", "reason_code", e.getClass().getSimpleName()),
                e);
            throw e;
        }
    }

    private long calculateDurationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String safeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
