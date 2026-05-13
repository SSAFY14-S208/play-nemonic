package com.nemonicworld.artifact.service.download;

public interface ArtifactDownloadStorage {

    boolean exists(String objectKey);

    byte[] download(String objectKey);

    void upload(String objectKey, byte[] bytes, String contentType);
}
