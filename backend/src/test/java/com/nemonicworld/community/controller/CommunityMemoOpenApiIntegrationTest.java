package com.nemonicworld.community.controller;

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
 * Swagger/OpenAPI 문서에 커뮤니티 메모 목록 조회 API가 노출되는지 검증합니다.
 */
class CommunityMemoOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * /v3/api-docs 응답에 GET /api/v1/community/memos 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void communityMemoListApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].get.summary").value("커뮤니티 메모 목록 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].get.tags[0]").value("Community"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].get.parameters[*].name")
                .value(hasItems("Anonymous-User-UUID")))
            .andExpect(jsonPath(
                "$.paths['/api/v1/community/memos'].get.parameters[?(@.name == 'Anonymous-User-UUID')].required")
                .value(hasItems(false)))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].get.responses['200'].description")
                .value("커뮤니티 메모 목록 조회 성공"))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos'].get.responses['400'].description").value("잘못된 UUID 형식"));
    }
}
