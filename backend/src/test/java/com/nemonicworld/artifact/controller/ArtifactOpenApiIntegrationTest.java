package com.nemonicworld.artifact.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * Swagger/OpenAPI 문서에 산출물 API가 노출되는지 검증합니다.
 */
class ArtifactOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * /v3/api-docs 응답에 GET /api/v1/artifacts/{artifactId}/image-urls 문서 정보가 포함되는지
     * 확인합니다.
     */
    @Test
    void artifactImageUrlApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/artifacts/{artifactId}/image-urls'].get.summary").value("산출물 이미지 URL 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/image-urls'].get.tags[0]").value("Artifact"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/image-urls'].get.parameters[*].name")
                .value(hasItems("Anonymous-User-UUID", "artifactId")))
            .andExpect(jsonPath(
                "$.paths['/api/v1/artifacts/{artifactId}/image-urls'].get.parameters[?(@.name == 'artifactId')].in")
                .value(hasItems("path")))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/image-urls'].get.responses['200'].description")
                .value("산출물 이미지 URL 조회 성공"));
    }
}
