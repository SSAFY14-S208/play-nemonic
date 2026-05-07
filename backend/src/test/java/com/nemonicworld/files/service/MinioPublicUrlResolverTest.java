package com.nemonicworld.files.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.files.config.MinioStorageProperties;
import org.junit.jupiter.api.Test;

class MinioPublicUrlResolverTest {

    @Test
    void resolveBuildsPublicUrlFromObjectKey() {
        MinioPublicUrlResolver resolver = new MinioPublicUrlResolver(storageProperties());

        String publicUrl = resolver.resolve("relay/results/a b/original image.png");

        assertThat(publicUrl).isEqualTo("http://localhost:9000/nemonic-local/relay/results/a%20b/original%20image.png");
    }

    @Test
    void resolveKeepsAlreadyCompletedUrlAsIs() {
        MinioPublicUrlResolver resolver = new MinioPublicUrlResolver(storageProperties());

        String publicUrl = resolver.resolve("https://cdn.example.com/images/result.png");

        assertThat(publicUrl).isEqualTo("https://cdn.example.com/images/result.png");
    }

    @Test
    void resolveReturnsNullWhenObjectReferenceIsBlank() {
        MinioPublicUrlResolver resolver = new MinioPublicUrlResolver(storageProperties());

        String publicUrl = resolver.resolve(" ");

        assertThat(publicUrl).isNull();
    }

    @Test
    void resolveTrimsConfiguredSlashes() {
        MinioPublicUrlResolver resolver = new MinioPublicUrlResolver(new MinioStorageProperties("http://minio:9000",
            "http://localhost:9000/", "minioadmin", "minioadmin", "/nemonic-local/", 10, 10 * 1024 * 1024));

        String publicUrl = resolver.resolve("/flipbook/results/result.gif/");

        assertThat(publicUrl).isEqualTo("http://localhost:9000/nemonic-local/flipbook/results/result.gif");
    }

    private MinioStorageProperties storageProperties() {
        return new MinioStorageProperties("http://minio:9000", "http://localhost:9000", "minioadmin", "minioadmin",
            "nemonic-local", 10, 10 * 1024 * 1024);
    }
}
