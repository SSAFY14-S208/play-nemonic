package com.nemonicworld.global.config;

import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiGroups.GroupDefinition;
import com.nemonicworld.common.openapi.OpenApiGroups;
import com.nemonicworld.common.openapi.OpenApiTags;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
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
        Components components = OpenApiCommonResponses.register(new Components()).addSecuritySchemes(BEARER_AUTH_SCHEME,
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));

        return new OpenAPI().components(components)
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
        return groupedOpenApi(OpenApiGroups.ALL_GROUP, openApiTagOrderCustomizer);
    }

    @Bean
    public GroupedOpenApi commonApiGroup() {
        return groupedOpenApi(OpenApiGroups.COMMON_GROUP);
    }

    @Bean
    public GroupedOpenApi contentsApiGroup() {
        return groupedOpenApi(OpenApiGroups.CONTENTS_GROUP);
    }

    @Bean
    public GroupedOpenApi gamesApiGroup() {
        return groupedOpenApi(OpenApiGroups.GAMES_GROUP);
    }

    @Bean
    public GroupedOpenApi supportLogsApiGroup() {
        return groupedOpenApi(OpenApiGroups.SUPPORT_LOGS_GROUP);
    }

    @Bean
    public GroupedOpenApi backofficeApiGroup() {
        return groupedOpenApi(OpenApiGroups.BACKOFFICE_GROUP);
    }

    private GroupedOpenApi groupedOpenApi(GroupDefinition groupDefinition) {
        return groupedOpenApi(groupDefinition, tagOrderCustomizer(groupDefinition.tagNames()));
    }

    private GroupedOpenApi groupedOpenApi(GroupDefinition groupDefinition,
        OpenApiCustomizer openApiTagOrderCustomizer) {
        return GroupedOpenApi.builder().group(groupDefinition.name()).displayName(groupDefinition.displayName())
            .pathsToMatch(groupDefinition.pathsToMatch().toArray(String[]::new))
            .addOpenApiCustomizer(openApiTagOrderCustomizer).build();
    }

    private OpenApiCustomizer tagOrderCustomizer(List<String> tagNames) {
        return openApi -> openApi.setTags(OpenApiTags.orderedTags(tagNames));
    }
}
