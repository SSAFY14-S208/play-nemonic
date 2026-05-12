package com.nemonicworld.global.config;

import com.nemonicworld.common.openapi.OpenApiTags;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
/**
 * Swagger UI와 OpenAPI 문서에 표시할 기본 API 정보를 설정합니다.
 */
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    /**
     * springdoc-openapi가 사용할 문서 제목, 버전, 설명을 제공합니다.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .info(new Info().title("Nemonic World API").version("v1").description("Nemonic World backend API"))
            .tags(OpenApiTags.orderedTags());
    }

    /**
     * springdoc이 컨트롤러 스캔 순서로 태그를 다시 정렬한 뒤에도 Swagger UI 표시 순서를 고정합니다.
     */
    @Bean
    public OpenApiCustomizer openApiTagOrderCustomizer() {
        return openApi -> openApi.setTags(OpenApiTags.orderedTags());
    }

    @Bean
    public GroupedOpenApi allApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("all", "전체 API", openApiTagOrderCustomizer, "/api/v1/**", "/api/logs/**");
    }

    @Bean
    public GroupedOpenApi commonApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("common", "공통", openApiTagOrderCustomizer, "/api/v1/users/**", "/api/v1/auth/**",
            "/api/v1/invites/**", "/api/v1/files/**");
    }

    @Bean
    public GroupedOpenApi contentsApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("contents", "콘텐츠", openApiTagOrderCustomizer, "/api/v1/gallery", "/api/v1/gallery/**",
            "/api/v1/artifacts/**", "/api/v1/share", "/api/v1/share/**", "/api/v1/community/memos",
            "/api/v1/community/memos/**", "/api/v1/fortune", "/api/v1/fortune/**");
    }

    @Bean
    public GroupedOpenApi gamesApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("games", "게임", openApiTagOrderCustomizer, "/api/v1/relay/rooms", "/api/v1/relay/rooms/**",
            "/api/v1/flipbook/rooms", "/api/v1/flipbook/rooms/**");
    }

    @Bean
    public GroupedOpenApi supportLogsApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("support-logs", "문의·로그", openApiTagOrderCustomizer, "/api/v1/inquiries",
            "/api/v1/inquiries/**", "/api/logs/**");
    }

    @Bean
    public GroupedOpenApi backofficeApiGroup(OpenApiCustomizer openApiTagOrderCustomizer) {
        return groupedOpenApi("backoffice", "백오피스", openApiTagOrderCustomizer, "/api/v1/admins", "/api/v1/admins/**",
            "/api/v1/admin/**", "/api/v1/backoffice/**");
    }

    private GroupedOpenApi groupedOpenApi(String group, String displayName, OpenApiCustomizer openApiTagOrderCustomizer,
        String... pathsToMatch) {
        return GroupedOpenApi.builder().group(group).displayName(displayName).pathsToMatch(pathsToMatch)
            .addOpenApiCustomizer(openApiTagOrderCustomizer).build();
    }
}
