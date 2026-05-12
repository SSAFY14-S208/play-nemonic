package com.nemonicworld.global.storage.minio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DB에 저장된 MinIO 객체 키를 브라우저에서 접근 가능한 공개 URL로 변환합니다.
 */
@Component
public class MinioPublicUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(MinioPublicUrlResolver.class);

    private final MinioStorageProperties minioStorageProperties;

    public MinioPublicUrlResolver(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    /**
     * 이미 완성된 URL은 그대로 반환하고, 객체 키는 publicUrl/bucket과 조합합니다.
     */
    public String resolve(String objectReference) {
        if (!StringUtils.hasText(objectReference)) {
            return null;
        }

        String trimmedObjectReference = objectReference.trim();
        if (isAbsoluteUrl(trimmedObjectReference)) {
            return trimmedObjectReference;
        }

        String publicUrl = trimTrailingSlashes(minioStorageProperties.publicUrl());
        String bucket = trimSlashes(minioStorageProperties.bucket());
        String objectKey = trimSlashes(trimmedObjectReference);
        if (!StringUtils.hasText(publicUrl) || !StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            log.warn("MinIO 공개 URL을 생성할 수 없습니다. publicUrl={}, bucket={}, objectReference={}", publicUrl, bucket,
                objectReference);

            return null;
        }

        try {
            return "%s/%s/%s".formatted(publicUrl, encodePathSegment(bucket), encodeObjectKey(objectKey));
        } catch (RuntimeException e) {
            log.warn("MinIO 공개 URL 생성 중 오류가 발생했습니다. objectReference={}", objectReference, e);

            return null;
        }
    }

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String encodeObjectKey(String objectKey) {
        return Arrays.stream(objectKey.split("/")).map(this::encodePathSegment)
            .reduce((left, right) -> left + "/" + right).orElse("");
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return null;
        }

        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String trimTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }

        return value.replaceAll("/+$", "");
    }
}
