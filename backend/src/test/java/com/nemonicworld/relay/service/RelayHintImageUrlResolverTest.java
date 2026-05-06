package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.files.config.MinioStorageProperties;
import com.nemonicworld.relay.service.assignment.RelayHintImageUrlResolver;
import org.junit.jupiter.api.Test;

class RelayHintImageUrlResolverTest {

    @Test
    void resolveCreatesPublicUrlWithoutDuplicateSlash() {
        RelayHintImageUrlResolver resolver = new RelayHintImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000/", "access", "secret", "nemonic-local", 10, 1024));

        String url = resolver.resolve("relay/tmp/AB3K9Q/1/face-hint.png");

        assertThat(url).isEqualTo("http://localhost:9000/nemonic-local/relay/tmp/AB3K9Q/1/face-hint.png");
    }

    @Test
    void resolveTrimsAndEncodesObjectKeyPathSegments() {
        RelayHintImageUrlResolver resolver = new RelayHintImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000", "access", "secret", "/nemonic-local/", 10, 1024));

        String url = resolver.resolve(" /relay/tmp/AB3K9Q/1/face hint.png ");

        assertThat(url).isEqualTo("http://localhost:9000/nemonic-local/relay/tmp/AB3K9Q/1/face%20hint.png");
    }

    @Test
    void resolveReturnsNullWhenObjectKeyIsBlank() {
        RelayHintImageUrlResolver resolver = new RelayHintImageUrlResolver(new MinioStorageProperties(
            "http://minio:9000", "http://localhost:9000", "access", "secret", "nemonic-local", 10, 1024));

        assertThat(resolver.resolve(" ")).isNull();
    }

    @Test
    void resolveReturnsNullWhenPublicUrlIsMissing() {
        RelayHintImageUrlResolver resolver = new RelayHintImageUrlResolver(
            new MinioStorageProperties("http://minio:9000", null, "access", "secret", "nemonic-local", 10, 1024));

        assertThat(resolver.resolve("relay/tmp/AB3K9Q/1/face-hint.png")).isNull();
    }
}
