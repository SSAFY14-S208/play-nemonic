package com.nemonicworld.relay.service.assignment;

import com.nemonicworld.files.config.MinioStorageProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 릴레이 힌트 이미지 object key를 브라우저에서 접근 가능한 public URL로 변환합니다.
 */
@Component
public class RelayHintImageUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(RelayHintImageUrlResolver.class);

    private final MinioStorageProperties minioStorageProperties;

    public RelayHintImageUrlResolver(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    /**
     * MinIO 파일 존재 확인이나 presigned URL 발급 없이 설정값과 object key만 조합합니다.
     */
    public String resolve(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }

        String publicUrl = trimTrailingSlashes(minioStorageProperties.publicUrl());
        String bucket = trimSlashes(minioStorageProperties.bucket());
        String normalizedObjectKey = trimSlashes(objectKey.trim());
        if (!StringUtils.hasText(publicUrl) || !StringUtils.hasText(bucket)
            || !StringUtils.hasText(normalizedObjectKey)) {
            log.warn("릴레이 힌트 이미지 URL을 생성할 수 없습니다. publicUrl={}, bucket={}, objectKey={}", publicUrl, bucket, objectKey);

            return null;
        }

        try {
            return "%s/%s/%s".formatted(publicUrl, encodePathSegment(bucket), encodeObjectKey(normalizedObjectKey));
        } catch (RuntimeException e) {
            log.warn("릴레이 힌트 이미지 URL 생성 중 오류가 발생했습니다. objectKey={}", objectKey, e);

            return null;
        }
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
