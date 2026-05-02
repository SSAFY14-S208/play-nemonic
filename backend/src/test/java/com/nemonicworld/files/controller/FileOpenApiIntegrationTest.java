package com.nemonicworld.files.controller;

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
 * Swagger/OpenAPI 문서에 파일 API가 노출되는지 검증합니다.
 */
class FileOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * /v3/api-docs 응답에 POST /api/v1/files/presign 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void filePresignApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/files/presign'].post.summary").value("이미지 업로드 Presigned URL 발급"))
            .andExpect(jsonPath("$.paths['/api/v1/files/presign'].post.tags[0]").value("File"))
            .andExpect(jsonPath("$.paths['/api/v1/files/presign'].post.parameters[0].name").value("X-User-UUID"))
            .andExpect(jsonPath("$.paths['/api/v1/files/presign'].post.responses['200'].description")
                .value("Presigned URL 발급 성공"));
    }
}
