package com.nemonicworld.files.config;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration // Spring 설정 클래스
@EnableConfigurationProperties(MinioStorageProperties.class)
// MinioStorageConfig가 MinioStorageProperties의 설정값으로 MinioClient 생성
public class MinioStorageConfig {

    // Spring이 MinioClient를 Bean으로 보관 (FileService가 MinioClient를 주입 받아 사용)
    // FileService에서 필요할때마다 만들어서 사용하면 코드가 지저분해짐
    @Bean // 이 메서드가 반환하는 객체를 Spring이 관리하게 하라는 뜻
    @Primary
    public MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder().endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey()).build();
    }

    /**
     * 프론트/앱이 직접 접근할 presigned URL은 공개 origin 기준으로 서명합니다.
     *
     * <p>
     * MinIO Java SDK는 endpoint에 path를 허용하지 않으므로, public-url이 /minio 같은 path를 포함해도
     * origin만 사용해 presigned URL을 생성합니다. 실제 반환 URL의 path prefix는 FileService에서 붙입니다.
     */
    @Bean
    public MinioClient publicMinioClient(MinioStorageProperties properties) {
        return MinioClient.builder().endpoint(publicOrigin(properties.publicUrl()))
            .credentials(properties.accessKey(), properties.secretKey()).build();
    }

    private String publicOrigin(String publicUrl) {
        URI uri = URI.create(publicUrl);
        String port = uri.getPort() == -1 ? "" : ":%d".formatted(uri.getPort());

        return "%s://%s%s".formatted(uri.getScheme(), uri.getHost(), port);
    }
}
