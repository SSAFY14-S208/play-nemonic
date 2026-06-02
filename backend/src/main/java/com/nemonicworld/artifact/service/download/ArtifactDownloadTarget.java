package com.nemonicworld.artifact.service.download;

public record ArtifactDownloadTarget(String objectKey, String sourceContentType, String resultContentType,
    String extension) {
}
