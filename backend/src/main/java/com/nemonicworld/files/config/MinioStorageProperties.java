package com.nemonicworld.files.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nemonic.storage.minio")
// application.yaml 값을 java 객체로 들고 있는 클래스 (application.yaml의 설정값을 읽음)
public record MinioStorageProperties(String endpoint, String publicUrl, String accessKey, String secretKey,
    String bucket, long presignExpirationMinutes, long maxUploadByteSize) {
    public int presignExpirationSeconds() {
        return Math.toIntExact(presignExpirationMinutes * 60);
    }
}
