package com.nemonicworld.gms.controller;

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
class GmsPromptOpenApiIntegrationTest {

    private static final String PREVIEW_REQUEST_EXAMPLES_PATH = "$.paths['/api/v1/backoffice/gms/prompts/preview']"
        + ".post.requestBody.content['application/json'].examples";
    private static final String CREATE_REQUEST_EXAMPLES_PATH = "$.paths['/api/v1/backoffice/gms/prompts']"
        + ".post.requestBody.content['application/json'].examples";
    private static final String TEST_REQUEST_EXAMPLES_PATH = "$.paths['/api/v1/backoffice/gms/prompts/{promptId}/test']"
        + ".post.requestBody.content['application/json'].examples";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void gmsPromptApisAreExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.summary").value("GMS 프롬프트 미리보기"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts'].post.summary").value("GMS 프롬프트 생성"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts'].get.summary").value("GMS 프롬프트 목록 조회"))
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/current'].get.summary").value("현재 GMS 프롬프트 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts/{promptId}/activate'].post.summary")
                .value("GMS 프롬프트 활성화"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts/{promptId}/test'].post.summary")
                .value("저장된 GMS 프롬프트 테스트"))
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.security[0].bearerAuth").exists())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/current'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath(PREVIEW_REQUEST_EXAMPLES_PATH).exists())
            .andExpect(jsonPath(CREATE_REQUEST_EXAMPLES_PATH).exists())
            .andExpect(jsonPath(TEST_REQUEST_EXAMPLES_PATH).exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptResponse.properties.isActive").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptResponse.properties.status").exists())
            .andExpect(
                jsonPath("$.components.schemas.GmsPromptResponse.properties.status.description").value("프론트 표시용 활성 상태"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptCurrentResponse.properties.source").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptTestRequest.properties.sampleSaju").exists());
    }
}
