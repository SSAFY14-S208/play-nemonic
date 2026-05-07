package com.nemonicworld.community.service;

import com.nemonicworld.files.config.MinioStorageProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 메모 이미지 참조 값을 브라우저에서 접근 가능한 public URL로 변환합니다.
 */
@Component
public class CommunityMemoImageUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(CommunityMemoImageUrlResolver.class);

    private final MinioStorageProperties minioStorageProperties;

    public CommunityMemoImageUrlResolver(MinioStorageProperties minioStorageProperties) {
        this.minioStorageProperties = minioStorageProperties;
    }

    /**
     * 절대 URL은 그대로 반환하고, object key는 MinIO 파일 존재 확인이나 presigned URL 발급 없이 설정값과
     * 조합합니다.
     */
    public String resolve(String imageReference) {
        if (!StringUtils.hasText(imageReference)) {
            return null;
        }

        String trimmedImageReference = imageReference.trim();
        if (isAbsoluteUrl(trimmedImageReference)) {
            return trimmedImageReference;
        }

        String publicUrl = trimTrailingSlashes(minioStorageProperties.publicUrl());
        String bucket = trimSlashes(minioStorageProperties.bucket());
        String normalizedObjectKey = trimSlashes(trimmedImageReference);
        if (!StringUtils.hasText(publicUrl) || !StringUtils.hasText(bucket)
            || !StringUtils.hasText(normalizedObjectKey)) {
            log.warn("커뮤니티 메모 이미지 URL을 생성할 수 없습니다. publicUrl={}, bucket={}, imageReference={}", publicUrl, bucket,
                imageReference);

            return null;
        }

        try {
            return "%s/%s/%s".formatted(publicUrl, encodePathSegment(bucket), encodeObjectKey(normalizedObjectKey));
        } catch (RuntimeException e) {
            log.warn("커뮤니티 메모 이미지 URL 생성 중 오류가 발생했습니다. imageReference={}", imageReference, e);

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
