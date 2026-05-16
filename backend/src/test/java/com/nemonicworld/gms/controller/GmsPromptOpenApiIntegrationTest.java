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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void gmsPromptPreviewApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.summary").value("GMS 프롬프트 미리보기"))
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.security[0].bearerAuth").exists())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.requestBody.required").value(true))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.responses['200'].description")
                .value("GMS 프롬프트 미리보기 성공"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.featureType").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.featureType.description")
                .value("미리보기 대상 기능 타입"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.content").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.content.description")
                .value("저장 전 테스트할 GMS 프롬프트 템플릿 본문"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.sampleSaju").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewRequest.properties.sampleSaju.description")
                .value("운세 미리보기에 사용할 샘플 사주 정보"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.featureType").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.fortune").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.fortune.description")
                .value("후보 프롬프트로 생성한 운세 결과"))
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.saju").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.design").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.previewImageBase64").exists())
            .andExpect(
                jsonPath("$.components.schemas.GmsPromptPreviewResponse.properties.previewImageBase64.description")
                    .value("렌더링된 미리보기 PNG의 Base64 데이터 URL"));
    }
}
