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
 * Swagger/OpenAPI 문서에 커뮤니티 메모 조회 API가 노출되는지 검증합니다.
 */
class CommunityMemoOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String DETAIL_HEADER_REQUIRED_JSON_PATH = """
        $.paths['/api/v1/community/memos/{memoId}'].get.parameters[?(@.name == 'Anonymous-User-UUID')].required
        """.trim();

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
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].get.responses['400'].description").value("잘못된 요청"))
            .andExpect(
                jsonPath("$.components.schemas.CommunityMemoItemResponse.properties.memoOriginalImageUrl").exists())
            .andExpect(
                jsonPath("$.components.schemas.CommunityMemoItemResponse.properties.memoThumbnailImageUrl").exists());
    }

    /**
     * /v3/api-docs 응답에 GET /api/v1/community/memos/{memoId} 문서 정보가 포함되는지 확인합니다.
     */
    @Test
    void communityMemoDetailApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.summary").value("커뮤니티 메모 상세 조회"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.tags[0]").value("Community"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.parameters[*].name")
                .value(hasItems("memoId", "Anonymous-User-UUID")))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.parameters[?(@.name == 'memoId')].required")
                    .value(hasItems(true)))
            .andExpect(jsonPath(DETAIL_HEADER_REQUIRED_JSON_PATH).value(hasItems(false)))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.responses['200'].description")
                .value("커뮤니티 메모 상세 조회 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.responses['400'].description")
                .value("잘못된 요청"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].get.responses['404'].description")
                .value("존재하지 않는 커뮤니티 메모"))
            .andExpect(
                jsonPath("$.components.schemas.CommunityMemoDetailResponse.properties.memoOriginalImageUrl").exists())
            .andExpect(
                jsonPath("$.components.schemas.CommunityMemoDetailResponse.properties.memoThumbnailImageUrl").exists());
    }

    @Test
    void communityMemoCreateApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].post.summary").value("커뮤니티 메모 생성"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].post.tags[0]").value("Community"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].post.parameters[*].name")
                .value(hasItems("Anonymous-User-UUID")))
            .andExpect(jsonPath(
                "$.paths['/api/v1/community/memos'].post.parameters[?(@.name == 'Anonymous-User-UUID')].required")
                .value(hasItems(true)))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].post.requestBody.required").value(true))
            .andExpect(jsonPath("$.components.schemas.CommunityMemoCreateRequest.properties.originalFileId").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoCreateRequest.properties.thumbnailFileId").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoCreateRequest.properties.sourceGalleryId").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoCreateRequest.properties.clientText").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoCreateRequest.properties.fileId").doesNotExist())
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos'].post.responses['201'].description").value("커뮤니티 메모 생성 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos'].post.responses['400'].description").value("요청값 오류"))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos'].post.responses['403'].description").value("파일 접근 권한 없음"))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos'].post.responses['404'].description").value("사용자 또는 파일 없음"))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos'].post.responses['409'].description").value("파일 업로드 상태 오류"));
    }

    @Test
    void communityMemoLayoutUpdateApiIsExposedInOpenApiDocs() throws Exception {
        String parametersPath = "$.paths['/api/v1/community/memos/{memoId}'].patch.parameters";

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.summary").value("커뮤니티 메모 위치 수정"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.tags[0]").value("Community"))
            .andExpect(jsonPath(parametersPath + "[*].name").value(hasItems("memoId", "Anonymous-User-UUID")))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'memoId')].required").value(hasItems(true)))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'Anonymous-User-UUID')].required").value(hasItems(true)))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.requestBody.required").value(true))
            .andExpect(jsonPath("$.components.schemas.CommunityMemoLayoutUpdateRequest.properties.positionX").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoLayoutUpdateRequest.properties.positionY").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoLayoutUpdateRequest.properties.zIndex").exists())
            .andExpect(
                jsonPath("$.components.schemas.CommunityMemoLayoutUpdateRequest.properties.rotationDeg").exists())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.responses['200'].description")
                .value("커뮤니티 메모 위치 수정 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.responses['400'].description")
                .value("잘못된 요청"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.responses['403'].description")
                .value("커뮤니티 메모 위치 수정 권한 없음"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].patch.responses['404'].description")
                .value("사용자 또는 커뮤니티 메모 없음"));
    }

    @Test
    void communityMemoDeleteApiIsExposedInOpenApiDocs() throws Exception {
        String parametersPath = "$.paths['/api/v1/community/memos/{memoId}'].delete.parameters";

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.summary").value("커뮤니티 메모 삭제"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.tags[0]").value("Community"))
            .andExpect(jsonPath(parametersPath + "[*].name").value(hasItems("memoId", "Anonymous-User-UUID")))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'memoId')].required").value(hasItems(true)))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'Anonymous-User-UUID')].required").value(hasItems(true)))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.responses['200'].description")
                .value("커뮤니티 메모 삭제 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.responses['400'].description")
                .value("잘못된 요청"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.responses['403'].description")
                .value("커뮤니티 메모 삭제 권한 없음"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}'].delete.responses['404'].description")
                .value("사용자 또는 커뮤니티 메모 없음"));
    }

    @Test
    void communityMemoReportApiIsExposedInOpenApiDocs() throws Exception {
        String parametersPath = "$.paths['/api/v1/community/memos/{memoId}/reports'].post.parameters";

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.summary").value("커뮤니티 메모 신고"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.tags[0]").value("Community"))
            .andExpect(jsonPath(parametersPath + "[*].name").value(hasItems("memoId", "Anonymous-User-UUID")))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'memoId')].required").value(hasItems(true)))
            .andExpect(jsonPath(parametersPath + "[?(@.name == 'Anonymous-User-UUID')].required").value(hasItems(true)))
            .andExpect(
                jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.requestBody.required").value(true))
            .andExpect(jsonPath("$.components.schemas.CommunityMemoReportRequest.properties.reason").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoReportRequest.properties.reasonDetail").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoReportResponse.properties.memoId").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoReportResponse.properties.reportCount").exists())
            .andExpect(jsonPath("$.components.schemas.CommunityMemoReportResponse.properties.hidden").exists())
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.responses['201'].description")
                .value("커뮤니티 메모 신고 성공"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.responses['400'].description")
                .value("잘못된 요청"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.responses['404'].description")
                .value("사용자 또는 커뮤니티 메모 없음"))
            .andExpect(jsonPath("$.paths['/api/v1/community/memos/{memoId}/reports'].post.responses['409'].description")
                .value("중복 신고"));
    }
}
