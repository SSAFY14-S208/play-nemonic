package com.nemonicworld.artifact.service.download;

import java.util.UUID;

public record ArtifactQrAsset(UUID artifactId, String kind, String cacheObjectKey, String fileName, String contentType,
    String shareToken) {
}
