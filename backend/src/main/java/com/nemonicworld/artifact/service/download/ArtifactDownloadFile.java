package com.nemonicworld.artifact.service.download;

public record ArtifactDownloadFile(byte[] bytes, String fileName, String contentType) {
}
