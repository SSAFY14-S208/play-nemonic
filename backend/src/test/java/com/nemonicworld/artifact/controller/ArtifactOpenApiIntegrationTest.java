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
 * Swagger/OpenAPI 문서에 산출물 이미지 URL 조회 API가 산출물 카테고리로 노출되는지 검증합니다.
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

    /**
     * /v3/api-docs 응답에 QR 합성 산출물 다운로드 API 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void artifactDownloadApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/artifacts/{artifactId}/download'].get.summary").value("QR 합성 산출물 다운로드"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/download'].get.tags[0]").value("Artifact"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/download'].get.parameters[*].name")
                .value(hasItems("Anonymous-User-UUID", "artifactId")))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/download'].get.responses['200'].description")
                .value("산출물 다운로드 성공"));
    }

    /**
     * /v3/api-docs 응답에 artifact ID 기반 SNS 공유 정보 생성 API 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void artifactShareApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/artifacts/{artifactId}/share'].post.summary").value("산출물 SNS 공유 정보 생성"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/share'].post.tags[0]").value("Artifact"))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/share'].post.parameters[*].name")
                .value(hasItems("Anonymous-User-UUID", "artifactId")))
            .andExpect(jsonPath("$.paths['/api/v1/artifacts/{artifactId}/share'].post.responses['200'].description")
                .value("산출물 공유 정보 생성 성공"));
    }
}
