package com.nemonicworld.files.controller;

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

    /**
     * /v3/api-docs 응답에 POST /api/v1/files/{fileId}/confirm 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void fileConfirmApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}/confirm'].post.summary").value("파일 업로드 완료 확인"))
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}/confirm'].post.tags[0]").value("File"))
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}/confirm'].post.parameters[*].name")
                .value(hasItems("X-User-UUID", "fileId")))
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}/confirm'].post.responses['200'].description")
                .value("파일 업로드 확인 성공"));
    }

    /**
     * /v3/api-docs 응답에 DELETE /api/v1/files/{fileId} 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void fileDeleteApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}'].delete.summary").value("파일 삭제"))
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}'].delete.tags[0]").value("File"))
            .andExpect(jsonPath("$.paths['/api/v1/files/{fileId}'].delete.parameters[*].name")
                .value(hasItems("X-User-UUID", "fileId")))
            .andExpect(
                jsonPath("$.paths['/api/v1/files/{fileId}'].delete.responses['200'].description").value("파일 삭제 성공"));
    }
}
