package com.nemonicworld.common.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Swagger UI가 표시할 문서 그룹 드롭다운 순서를 고정합니다.
 */
@Hidden
@RestController
public class SwaggerUiConfigController {

    private static final String MAPPING_PATH = "/api-docs/swagger-config";
    private static final String PUBLIC_CONFIG_URL = "/api/v1/api-docs/swagger-config";

    @GetMapping(value = MAPPING_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> swaggerUiConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("configUrl", PUBLIC_CONFIG_URL);
        config.put("validatorUrl", "");
        config.put("urls", orderedUrls());
        config.put("urls.primaryName", OpenApiGroups.ALL_GROUP.displayName());

        return config;
    }

    private List<Map<String, String>> orderedUrls() {
        List<Map<String, String>> urls = new ArrayList<>();
        for (OpenApiGroups.GroupDefinition groupDefinition : OpenApiGroups.ORDERED_GROUPS) {
            Map<String, String> url = new LinkedHashMap<>();
            url.put("name", groupDefinition.displayName());
            url.put("url", groupDefinition.apiDocsUrl());
            urls.add(url);
        }

        return urls;
    }
}
