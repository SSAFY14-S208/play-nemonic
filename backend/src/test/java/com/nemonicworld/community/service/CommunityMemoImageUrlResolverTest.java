package com.nemonicworld.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.files.config.MinioStorageProperties;
import org.junit.jupiter.api.Test;

class CommunityMemoImageUrlResolverTest {

    private static final String MEMO_OBJECT_KEY = "uploads/community/2026/05/07/"
        + "550e8400-e29b-41d4-a716-446655440000/memo image.png";
    private static final String MEMO_PUBLIC_URL = "http://localhost:9000/nemonic-local/uploads/community/2026/05/07/"
        + "550e8400-e29b-41d4-a716-446655440000/memo%20image.png";

    @Test
    void resolveCreatesPublicUrlForCommunityMemoObjectKey() {
        CommunityMemoImageUrlResolver resolver = new CommunityMemoImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000/", "access", "secret", "nemonic-local", 10, 1024));

        String url = resolver.resolve(MEMO_OBJECT_KEY);

        assertThat(url).isEqualTo(MEMO_PUBLIC_URL);
    }

    @Test
    void resolveTrimsSlashesAndEncodesObjectKeyPathSegments() {
        CommunityMemoImageUrlResolver resolver = new CommunityMemoImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000", "access", "secret", "/nemonic-local/", 10, 1024));

        String url = resolver.resolve(" /" + MEMO_OBJECT_KEY + " ");

        assertThat(url).isEqualTo(MEMO_PUBLIC_URL);
    }

    @Test
    void resolveReturnsNullWhenObjectKeyIsBlank() {
        CommunityMemoImageUrlResolver resolver = new CommunityMemoImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000", "access", "secret", "nemonic-local", 10, 1024));

        assertThat(resolver.resolve(" ")).isNull();
    }

    @Test
    void resolveReturnsNullWhenPublicUrlIsMissing() {
        CommunityMemoImageUrlResolver resolver = new CommunityMemoImageUrlResolver(
            new MinioStorageProperties("http://minio:9000", null, "access", "secret", "nemonic-local", 10, 1024));

        assertThat(resolver.resolve("uploads/community/2026/05/07/file-id/memo.png")).isNull();
    }

    @Test
    void resolveReturnsNullWhenBucketIsMissing() {
        CommunityMemoImageUrlResolver resolver = new CommunityMemoImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000", "access", "secret", " ", 10, 1024));

        assertThat(resolver.resolve("uploads/community/2026/05/07/file-id/memo.png")).isNull();
    }
}
