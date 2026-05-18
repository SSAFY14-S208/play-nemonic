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
    void gmsPromptApisAreExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.summary").value("GMS prompt preview"))
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/current'].get.summary").value("Current GMS prompt"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts/{promptId}/activate'].post.summary")
                .value("GMS prompt activate"))
            .andExpect(jsonPath("$.paths['/api/v1/backoffice/gms/prompts/{promptId}/test'].post.summary")
                .value("Saved GMS prompt test"))
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/preview'].post.security[0].bearerAuth").exists())
            .andExpect(
                jsonPath("$.paths['/api/v1/backoffice/gms/prompts/current'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptResponse.properties.isActive").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptResponse.properties.status").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptCurrentResponse.properties.source").exists())
            .andExpect(jsonPath("$.components.schemas.GmsPromptTestRequest.properties.sampleSaju").exists());
    }
}
