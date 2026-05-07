package com.nemonicworld.global.storage.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "nemonic.storage.minio")
// application.yaml 값을 java 객체로 들고 있는 클래스 (application.yaml의 설정값을 읽음)
public record MinioStorageProperties(String endpoint, String publicUrl, String accessKey, String secretKey,
    String bucket, long presignExpirationMinutes, long viewUrlExpirationMinutes, long maxUploadByteSize) {

    @ConstructorBinding
    public MinioStorageProperties {
    }

    public MinioStorageProperties(String endpoint, String publicUrl, String accessKey, String secretKey, String bucket,
        long presignExpirationMinutes, long maxUploadByteSize) {
        this(endpoint, publicUrl, accessKey, secretKey, bucket, presignExpirationMinutes, 1440, maxUploadByteSize);
    }

    public int presignExpirationSeconds() {
        return Math.toIntExact(presignExpirationMinutes * 60);
    }

    public int viewUrlExpirationSeconds() {
        return Math.toIntExact(viewUrlExpirationMinutes * 60);
    }
}
