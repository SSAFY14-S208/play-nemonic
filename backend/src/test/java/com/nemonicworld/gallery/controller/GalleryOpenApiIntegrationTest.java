package com.nemonicworld.gallery.controller;

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
 * Swagger/OpenAPI 문서에 갤러리 API가 노출되는지 검증합니다.
 */
class GalleryOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * /v3/api-docs 응답에 GET /api/v1/gallery 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void myGalleryApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/gallery'].get.summary").value("내 갤러리 목록 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery'].get.tags[0]").value("Gallery"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery'].get.responses['200'].description").value("내 갤러리 목록 조회 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].get.summary").value("내 갤러리 항목 상세 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].get.tags[0]").value("Gallery"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].get.responses['200'].description")
                .value("내 갤러리 항목 상세 조회 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].delete.summary").value("갤러리 항목 삭제"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].delete.tags[0]").value("Gallery"))
            .andExpect(jsonPath("$.paths['/api/v1/gallery/{galleryId}'].delete.responses['200'].description")
                .value("갤러리 항목 삭제 성공"));
    }
}
