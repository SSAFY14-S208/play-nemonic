package com.nemonicworld.files.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Spring 설정 클래스
@EnableConfigurationProperties(MinioStorageProperties.class)
// MinioStorageConfig가 MinioStorageProperties의 설정값으로 MinioClient 생성
public class MinioStorageConfig {

    // Spring이 MinioClient를 Bean으로 보관 (FileService가 MinioClient를 주입 받아 사용)
    // FileService에서 필요할때마다 만들어서 사용하면 코드가 지저분해짐
    @Bean // 이 메서드가 반환하는 객체를 Spring이 관리하게 하라는 뜻
    public MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder().endpoint(properties.publicUrl())
            .credentials(properties.accessKey(), properties.secretKey()).build();
    }
}
