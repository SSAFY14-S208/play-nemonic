package com.nemonicworld.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * Swagger UI와 OpenAPI 문서에 표시할 기본 API 정보를 설정합니다.
 */
public class OpenApiConfig {

    /**
     * springdoc-openapi가 사용할 문서 제목, 버전, 설명을 제공합니다.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("Nemonic World API").version("v1").description("Nemonic World backend API"));
    }
}
