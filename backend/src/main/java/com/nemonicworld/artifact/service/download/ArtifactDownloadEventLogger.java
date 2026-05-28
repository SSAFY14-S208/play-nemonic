package com.nemonicworld.artifact.service.download;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ArtifactDownloadEventLogger {

    private static final String CONTENT_TYPE_ARTIFACT = "artifact";
    private static final String ARTIFACT_DOWNLOAD_REQUESTED_EVENT = "artifact_download_requested";
    private static final String ARTIFACT_DOWNLOAD_CREATED_EVENT = "artifact_download_created";
    private static final String ARTIFACT_DOWNLOAD_FAILED_EVENT = "artifact_download_failed";

    public long startTimer() {
        return System.nanoTime();
    }

    public void logRequested(String userUuidValue, String artifactIdValue) {
        StructuredEventLogger.apiBusiness(ARTIFACT_DOWNLOAD_REQUESTED_EVENT, CONTENT_TYPE_ARTIFACT,
            safeUuid(userUuidValue),
            StructuredEventLogger.metadata("artifact_id", artifactIdValue, "result", "requested"));
    }

    public void logCreated(String userUuidValue, ArtifactQrAsset asset, int byteSize, long startedAt) {
        StructuredEventLogger.apiBusiness(ARTIFACT_DOWNLOAD_CREATED_EVENT, asset.kind(), safeUuid(userUuidValue),
            StructuredEventLogger.metadata("artifact_id", asset.artifactId(), "file_name", asset.fileName(),
                "content_type", asset.contentType(), "byte_size", byteSize, "duration_ms",
                calculateDurationMs(startedAt), "result", "success"));
    }

    public void logFailed(String userUuidValue, String artifactIdValue, RuntimeException error, long startedAt) {
        StructuredEventLogger.apiBusinessWarn(ARTIFACT_DOWNLOAD_FAILED_EVENT, CONTENT_TYPE_ARTIFACT,
            safeUuid(userUuidValue), "artifact download failed",
            StructuredEventLogger.metadata("artifact_id", artifactIdValue, "duration_ms",
                calculateDurationMs(startedAt), "result", "failed", "reason_code", error.getClass().getSimpleName()),
            error);
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
