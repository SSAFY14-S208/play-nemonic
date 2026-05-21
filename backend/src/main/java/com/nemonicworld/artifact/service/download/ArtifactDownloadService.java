package com.nemonicworld.artifact.service.download;

public interface ArtifactDownloadService {

    ArtifactDownloadFile prepareDownloadFile(String userUuidValue, String artifactIdValue);
}
