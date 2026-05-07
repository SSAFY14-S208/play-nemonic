package com.nemonicworld.files.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MinioStorageConfigTest {

    @Test
    void publicMinioClientAcceptsPublicUrlWithPathPrefix() {
        MinioStorageConfig config = new MinioStorageConfig();
        MinioStorageProperties properties = new MinioStorageProperties("http://minio:9000",
            "https://k14s208.p.ssafy.io/minio", "minioadmin", "minioadmin", "nemonic", 10, 10 * 1024 * 1024);

        assertThatCode(() -> config.publicMinioClient(properties)).doesNotThrowAnyException();
    }
}
