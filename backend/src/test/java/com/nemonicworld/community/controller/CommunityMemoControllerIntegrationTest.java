package com.nemonicworld.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationException;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationRequest;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationResult;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 커뮤니티 메모 조회/생성 API가 최종 게시 이미지 스냅샷 계약을 지키는지 검증합니다.
 */
class CommunityMemoControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ORIGINAL_OBJECT_KEY = "uploads/community/2026/05/07/direct-user/original image.png";
    private static final String THUMBNAIL_OBJECT_KEY = "uploads/community/2026/05/07/direct-user/thumbnail image.png";
    private static final String ORIGINAL_PUBLIC_URL = "http://localhost:9000/nemonic-local/uploads/community/"
        + "2026/05/07/direct-user/original%20image.png";
    private static final String THUMBNAIL_PUBLIC_URL = "http://localhost:9000/nemonic-local/uploads/community/"
        + "2026/05/07/direct-user/thumbnail%20image.png";
    private static final String OBJECT_KEY_PREFIX = "uploads/community/2026/05/07/gallery/";
    private static final String PUBLIC_URL_PREFIX = "http://localhost:9000/nemonic-local/" + OBJECT_KEY_PREFIX;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommunityMemoModerationClient moderationClient;

    @BeforeEach
    void prepareCommunityTables() {
        when(moderationClient.check(any())).thenReturn(CommunityMemoModerationResult.allowedResult());
        createTables();
        cleanTables();
    }

    @Test
    void getCommunityMemosReturnsSnapshotImageUrlsAndKeepsVisibleOrdering() throws Exception {
        UUID userUuid = createExistingUser("망고");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID firstMemoId = insertDirectMemo(userUuid, "first-original.png", "first-thumbnail.png", 2,
            baseTime.plusMinutes(2), null, false);
        UUID secondMemoId = insertDirectMemo(userUuid, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1,
            baseTime.plusMinutes(2), null, false);
        UUID fallbackMemoId = insertDirectMemo(userUuid, "fallback-original.png", null, 1, baseTime.plusMinutes(1),
            null, false);

        insertDirectMemo(userUuid, "deleted-original.png", "deleted-thumbnail.png", 0, baseTime, baseTime, false);
        insertDirectMemo(userUuid, "hidden-original.png", "hidden-thumbnail.png", 0, baseTime, null, true);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("커뮤니티 메모 목록 조회 성공"))
            .andExpect(jsonPath("$.data.items", hasSize(3))).andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(fallbackMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].memoUuid").value(secondMemoId.toString()))
            .andExpect(jsonPath("$.data.items[2].memoUuid").value(firstMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].authorNickname").value("망고"))
            .andExpect(jsonPath("$.data.items[1].sourceType").value("DIRECT"))
            .andExpect(jsonPath("$.data.items[1].memoOriginalImageUrl").value(ORIGINAL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[1].memoThumbnailImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[1].memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[0].memoThumbnailImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.items[0].memoImageUrl")
                .value("http://localhost:9000/nemonic-local/fallback-original.png"))
            .andExpect(jsonPath("$.data.items[1].positionX").value(120.5))
            .andExpect(jsonPath("$.data.items[1].positionY").value(80.0))
            .andExpect(jsonPath("$.data.items[1].zIndex").value(1))
            .andExpect(jsonPath("$.data.items[1].rotationDeg").value(-4.5))
            .andExpect(jsonPath("$.data.items[1].ownedByMe").value(false))
            .andExpect(jsonPath("$.data.items[1].userId").doesNotExist())
            .andExpect(jsonPath("$.data.items[1].authorUuid").doesNotExist());
    }

    @Test
    void getCommunityMemoReturnsGalleryAttributionButUsesCommunitySnapshotImages() throws Exception {
        UUID userUuid = createExistingUser("출처표시");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();

        insertArtifact(artifactId, "relay_drawing", "artifact-thumbnail-should-not-be-used.png", now);
        insertCommunityMemo(memoId, userUuid, artifactId, OBJECT_KEY_PREFIX + "gallery-original.png",
            OBJECT_KEY_PREFIX + "gallery-thumbnail.png", 4, now, null, false, "{\"frame\":\"gold\"}", 1, "allowed",
            now.minusMinutes(1), now.plusMinutes(1));

        mockMvc
            .perform(
                get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.memoOriginalImageUrl").value(PUBLIC_URL_PREFIX + "gallery-original.png"))
            .andExpect(jsonPath("$.data.memoThumbnailImageUrl").value(PUBLIC_URL_PREFIX + "gallery-thumbnail.png"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(PUBLIC_URL_PREFIX + "gallery-thumbnail.png"))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.decoration.frame").value("gold"))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"))
            .andExpect(jsonPath("$.data.reportCount").value(1)).andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.authorUuid").doesNotExist());
    }

    @Test
    void createCommunityMemoCreatesDirectSnapshotMemoAfterModerationAllowed() throws Exception {
        UUID userUuid = createExistingUser("생성메모");
        UUID originalFileId = insertFileUpload(userUuid, ORIGINAL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, THUMBNAIL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        when(moderationClient.check(any())).thenReturn(new CommunityMemoModerationResult(true, "추출 텍스트",
            objectMapper.readTree("[{\"name\":\"abuse\",\"score\":0.01}]")));

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "originalFileId": "%s",
                      "thumbnailFileId": "%s",
                      "positionX": 12.5,
                      "positionY": -7.25,
                      "zIndex": 10,
                      "rotationDeg": 5.5,
                      "decoration": {
                        "layers": []
                      },
                      "clientText": "텍스트박스 원문"
                    }
                    """.formatted(originalFileId, thumbnailFileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 생성 성공"))
            .andExpect(jsonPath("$.data.authorNickname").value("생성메모"))
            .andExpect(jsonPath("$.data.sourceType").value("DIRECT"))
            .andExpect(jsonPath("$.data.memoOriginalImageUrl").value(ORIGINAL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.memoThumbnailImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.positionX").value(12.5)).andExpect(jsonPath("$.data.positionY").value(-7.25))
            .andExpect(jsonPath("$.data.zIndex").value(10)).andExpect(jsonPath("$.data.rotationDeg").value(5.5))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.decoration.layers", hasSize(0)))
            .andExpect(jsonPath("$.data.artifactId").value(nullValue()))
            .andExpect(jsonPath("$.data.galleryContentKind").value(nullValue()))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"))
            .andExpect(jsonPath("$.data.reportCount").value(0));

        UUID memoId = jdbcTemplate.queryForObject("SELECT id FROM community_memo WHERE body_image_url = ?", UUID.class,
            ORIGINAL_OBJECT_KEY);
        assertThat(memoId).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(THUMBNAIL_OBJECT_KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo("allowed");
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_checked_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isNotNull();
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_text FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("추출 텍스트");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_categories FROM community_memo WHERE id = ?", String.class, memoId))
            .contains("abuse");

        ArgumentCaptor<CommunityMemoModerationRequest> requestCaptor = ArgumentCaptor
            .forClass(CommunityMemoModerationRequest.class);
        verify(moderationClient).check(requestCaptor.capture());
        assertThat(requestCaptor.getValue().imageUrl()).isEqualTo(ORIGINAL_PUBLIC_URL);
        assertThat(requestCaptor.getValue().thumbnailUrl()).isEqualTo(THUMBNAIL_PUBLIC_URL);
        assertThat(requestCaptor.getValue().clientText()).isEqualTo("텍스트박스 원문");
        assertThat(requestCaptor.getValue().sourceType()).isEqualTo("DIRECT");

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.memoImageUrl").value(THUMBNAIL_PUBLIC_URL));
        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(memoId.toString()));
    }

    @Test
    void createCommunityMemoStoresEmptyDecorationAndEmptyClientTextWhenOmitted() throws Exception {
        UUID userUuid = createExistingUser("기본값");
        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/no-decoration-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/no-decoration-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "originalFileId": "%s",
                      "thumbnailFileId": "%s",
                      "positionX": 0.0,
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """.formatted(originalFileId, thumbnailFileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.decoration").value(anEmptyMap()));

        assertThat(jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE body_image_url = ?",
            String.class, "uploads/community/no-decoration-original.png")).isEqualTo("{}");
        ArgumentCaptor<CommunityMemoModerationRequest> requestCaptor = ArgumentCaptor
            .forClass(CommunityMemoModerationRequest.class);
        verify(moderationClient).check(requestCaptor.capture());
        assertThat(requestCaptor.getValue().thumbnailUrl())
            .isEqualTo("http://localhost:9000/nemonic-local/uploads/community/no-decoration-thumbnail.png");
        assertThat(requestCaptor.getValue().clientText()).isEmpty();
    }

    @Test
    void createCommunityMemoCreatesGallerySnapshotMemoWithSourceArtifact() throws Exception {
        UUID userUuid = createExistingUser("갤러리게시");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        UUID originalFileId = insertFileUpload(userUuid, OBJECT_KEY_PREFIX + "posted-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, OBJECT_KEY_PREFIX + "posted-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);
        insertArtifact(artifactId, "flipbook", "artifact-thumbnail-should-not-render.png", now);
        insertGallery(galleryId, userUuid, artifactId, null);
        when(moderationClient.check(any()))
            .thenReturn(new CommunityMemoModerationResult(true, "갤러리 OCR", objectMapper.readTree("[\"safe\"]")));

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "GALLERY",
                      "sourceGalleryId": "%s",
                      "originalFileId": "%s",
                      "thumbnailFileId": "%s",
                      "positionX": 12.5,
                      "positionY": -7.25,
                      "zIndex": 10,
                      "rotationDeg": 0.0,
                      "decoration": {
                        "scale": 1.0
                      },
                      "clientText": "사용자가 추가한 텍스트"
                    }
                    """.formatted(galleryId, originalFileId, thumbnailFileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value("flipbook"))
            .andExpect(jsonPath("$.data.memoOriginalImageUrl").value(PUBLIC_URL_PREFIX + "posted-original.png"))
            .andExpect(jsonPath("$.data.memoThumbnailImageUrl").value(PUBLIC_URL_PREFIX + "posted-thumbnail.png"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(PUBLIC_URL_PREFIX + "posted-thumbnail.png"))
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"));

        UUID memoId = jdbcTemplate.queryForObject("SELECT id FROM community_memo WHERE body_image_url = ?", UUID.class,
            OBJECT_KEY_PREFIX + "posted-original.png");
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isEqualTo(artifactId);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(OBJECT_KEY_PREFIX + "posted-thumbnail.png");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_categories FROM community_memo WHERE id = ?", String.class, memoId))
            .contains("safe");

        ArgumentCaptor<CommunityMemoModerationRequest> requestCaptor = ArgumentCaptor
            .forClass(CommunityMemoModerationRequest.class);
        verify(moderationClient).check(requestCaptor.capture());
        assertThat(requestCaptor.getValue().imageUrl()).isEqualTo(PUBLIC_URL_PREFIX + "posted-original.png");
        assertThat(requestCaptor.getValue().thumbnailUrl()).isEqualTo(PUBLIC_URL_PREFIX + "posted-thumbnail.png");
        assertThat(requestCaptor.getValue().clientText()).isEqualTo("사용자가 추가한 텍스트");
        assertThat(requestCaptor.getValue().sourceType()).isEqualTo("GALLERY");
    }

    @Test
    void createCommunityMemoRejectsOldFileIdShapeAndInvalidFileIds() throws Exception {
        UUID userUuid = createExistingUser("요청검증");
        UUID originalFileId = UUID.randomUUID();
        UUID thumbnailFileId = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "fileId": "%s",
                      "positionX": 0.0,
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """.formatted(originalFileId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 originalFileId 형식입니다."));
        mockMvc.perform(createRequest(userUuid, null, thumbnailFileId.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 originalFileId 형식입니다."));
        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 thumbnailFileId 형식입니다."));
        mockMvc.perform(createRequest(userUuid, "not-a-uuid", thumbnailFileId.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 originalFileId 형식입니다."));
        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), "not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 thumbnailFileId 형식입니다."));
        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), originalFileId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 원본과 썸네일 파일은 서로 달라야 합니다."));
    }

    @Test
    void createCommunityMemoValidatesSourcePositionAndDecoration() throws Exception {
        UUID userUuid = createExistingUser("필드검증");
        UUID originalFileId = UUID.randomUUID();
        UUID thumbnailFileId = UUID.randomUUID();

        mockMvc.perform(createRequestWithSource(userUuid, originalFileId, thumbnailFileId, "UNKNOWN", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지원하지 않는 커뮤니티 메모 sourceType입니다."));
        mockMvc.perform(createRequestWithSource(userUuid, originalFileId, thumbnailFileId, "GALLERY", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 sourceGalleryId 형식입니다."));
        mockMvc.perform(createRequestWithSource(userUuid, originalFileId, thumbnailFileId, "DIRECT", UUID.randomUUID()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 원본 정보가 올바르지 않습니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "originalFileId": "%s",
                      "thumbnailFileId": "%s",
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """.formatted(originalFileId, thumbnailFileId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(createRequestWithDecoration(userUuid, originalFileId, thumbnailFileId, "[1,2,3]"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."));
    }

    @Test
    void createCommunityMemoValidatesGallerySource() throws Exception {
        UUID userUuid = createExistingUser("갤러리검증");
        UUID otherUserUuid = createExistingUser("다른소유자");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/gallery-source-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/gallery-source-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);
        UUID ownedArtifactId = UUID.randomUUID();
        UUID otherArtifactId = UUID.randomUUID();
        UUID deletedArtifactId = UUID.randomUUID();
        UUID ownedGalleryId = UUID.randomUUID();
        UUID otherGalleryId = UUID.randomUUID();
        UUID deletedGalleryId = UUID.randomUUID();
        UUID orphanGalleryId = UUID.randomUUID();
        insertArtifact(ownedArtifactId, "relay_drawing", "owned-artifact.png", now);
        insertArtifact(otherArtifactId, "relay_drawing", "other-artifact.png", now);
        insertArtifact(deletedArtifactId, "relay_drawing", "deleted-artifact.png", now);
        insertGallery(ownedGalleryId, userUuid, ownedArtifactId, null);
        insertGallery(otherGalleryId, otherUserUuid, otherArtifactId, null);
        insertGallery(deletedGalleryId, userUuid, deletedArtifactId, now);
        insertGallery(orphanGalleryId, userUuid, UUID.randomUUID(), null);

        mockMvc.perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(), null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 sourceGalleryId 형식입니다."));
        mockMvc
            .perform(
                createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(), "not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 sourceGalleryId 형식입니다."));
        mockMvc
            .perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(),
                UUID.randomUUID().toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));
        mockMvc
            .perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(),
                otherGalleryId.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));
        mockMvc
            .perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(),
                deletedGalleryId.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));
        mockMvc
            .perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(),
                orphanGalleryId.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));

        mockMvc
            .perform(createGalleryRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString(),
                ownedGalleryId.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.artifactId").value(ownedArtifactId.toString()));
    }

    @Test
    void createCommunityMemoValidatesBothOriginalAndThumbnailFiles() throws Exception {
        UUID userUuid = createExistingUser("파일검증");
        UUID otherUserUuid = createExistingUser("타인");
        UUID validOriginalFileId = insertFileUpload(userUuid, "uploads/community/valid-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID validThumbnailFileId = insertFileUpload(userUuid, "uploads/community/valid-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);
        UUID otherUserFileId = insertFileUpload(otherUserUuid, "uploads/community/other.png", "COMMUNITY", "UPLOADED",
            null);
        UUID wrongPurposeFileId = insertFileUpload(userUuid, "uploads/community/wrong-purpose.png", "RELAY_DRAWING",
            "UPLOADED", null);
        UUID pendingFileId = insertFileUpload(userUuid, "uploads/community/pending.png", "COMMUNITY", "PENDING", null);
        UUID deletedFileId = insertFileUpload(userUuid, "uploads/community/deleted.png", "COMMUNITY", "DELETED", null);
        UUID softDeletedFileId = insertFileUpload(userUuid, "uploads/community/soft-deleted.png", "COMMUNITY",
            "UPLOADED", LocalDateTime.now());

        mockMvc.perform(createRequest(userUuid, UUID.randomUUID().toString(), validThumbnailFileId.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));
        mockMvc.perform(createRequest(userUuid, validOriginalFileId.toString(), UUID.randomUUID().toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));
        mockMvc.perform(createRequest(userUuid, otherUserFileId.toString(), validThumbnailFileId.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));
        mockMvc.perform(createRequest(userUuid, validOriginalFileId.toString(), otherUserFileId.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));
        mockMvc.perform(createRequest(userUuid, wrongPurposeFileId.toString(), validThumbnailFileId.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 원본 정보가 올바르지 않습니다."));
        mockMvc.perform(createRequest(userUuid, pendingFileId.toString(), validThumbnailFileId.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
        mockMvc.perform(createRequest(userUuid, validOriginalFileId.toString(), deletedFileId.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
        mockMvc.perform(createRequest(userUuid, softDeletedFileId.toString(), validThumbnailFileId.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
    }

    @Test
    void createCommunityMemoDoesNotInsertWhenModerationBlocksOrFailsClosed() throws Exception {
        UUID userUuid = createExistingUser("차단");
        UUID blockedOriginalFileId = insertFileUpload(userUuid, "uploads/community/blocked-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID blockedThumbnailFileId = insertFileUpload(userUuid, "uploads/community/blocked-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);
        when(moderationClient.check(any())).thenReturn(new CommunityMemoModerationResult(false, "나쁜 말", null));

        mockMvc.perform(createRequest(userUuid, blockedOriginalFileId.toString(), blockedThumbnailFileId.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("부적절한 표현이 감지되어 게시할 수 없습니다."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo", Integer.class)).isZero();

        UUID failedOriginalFileId = insertFileUpload(userUuid, "uploads/community/failed-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID failedThumbnailFileId = insertFileUpload(userUuid, "uploads/community/failed-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);
        doThrow(new CommunityMemoModerationException("timeout")).when(moderationClient).check(any());

        mockMvc.perform(createRequest(userUuid, failedOriginalFileId.toString(), failedThumbnailFileId.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 모더레이션을 완료할 수 없습니다."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo", Integer.class)).isZero();
    }

    @Test
    void createCommunityMemoExpiresOldestVisibleMemoWhenVisibleLimitIsExceeded() throws Exception {
        UUID userUuid = createExistingUser("FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        UUID oldestMemoId = null;
        for (int index = 0; index < 50; index++) {
            UUID memoId = insertDirectMemo(userUuid, "fifo-original-%02d.png".formatted(index),
                "fifo-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
            if (index == 0) {
                oldestMemoId = memoId;
            }
        }
        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/fifo-new-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/fifo-new-thumbnail.png", "COMMUNITY",
            "UPLOADED", null);

        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.memoImageUrl")
                .value("http://localhost:9000/nemonic-local/uploads/community/fifo-new-thumbnail.png"));

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class,
            oldestMemoId)).isEqualTo("expired");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE body_image_url = 'uploads/community/fifo-new-original.png'
              AND deleted_at IS NULL
            """, Integer.class)).isEqualTo(1);
    }

    @Test
    void createCommunityMemoDoesNotCountHiddenMemosForFifoLimit() throws Exception {
        UUID userUuid = createExistingUser("숨김제외");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        for (int index = 0; index < 49; index++) {
            insertDirectMemo(userUuid, "visible-original-%02d.png".formatted(index),
                "visible-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
        }
        UUID hiddenMemoId = insertDirectMemo(userUuid, "hidden-fifo-original.png", "hidden-fifo-thumbnail.png", 1,
            baseTime.minusMinutes(1), null, true);
        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/hidden-fifo-new-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/hidden-fifo-new-thumbnail.png",
            "COMMUNITY", "UPLOADED", null);

        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString()))
            .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, hiddenMemoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo WHERE deleted_reason = 'expired'",
            Integer.class)).isZero();
    }

    @Test
    void getCommunityMemoReturnsNotFoundForMissingDeletedAndHiddenMemos() throws Exception {
        UUID userUuid = createExistingUser("상세조건");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID deletedMemoId = insertDirectMemo(userUuid, "deleted-original.png", "deleted-thumbnail.png", 1, now, now,
            false);
        UUID hiddenMemoId = insertDirectMemo(userUuid, "hidden-original.png", "hidden-thumbnail.png", 2,
            now.plusMinutes(1), null, true);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", UUID.randomUUID())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", deletedMemoId)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", hiddenMemoId)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
    }

    @Test
    void getCommunityMemosRejectsInvalidViewerUuidAndCalculatesOwnedByMe() throws Exception {
        UUID userUuid = createExistingUser("소유자");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        insertDirectMemo(userUuid, "owned-original.png", "owned-thumbnail.png", 1, now, null, false);

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].ownedByMe").value(true));
    }

    private void createTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id UUID PRIMARY KEY,
                kind VARCHAR(32) NOT NULL,
                source_room_id VARCHAR(64) NULL,
                thumbnail_url VARCHAR(1000) NOT NULL,
                meta VARCHAR(1000) NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS gallery (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS file_upload (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                purpose VARCHAR(32) NOT NULL,
                original_file_name VARCHAR(255) NOT NULL,
                content_type VARCHAR(100) NOT NULL,
                byte_size BIGINT NOT NULL,
                object_key VARCHAR(1000) NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS community_memo (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NULL,
                position_x DOUBLE PRECISION NOT NULL DEFAULT 0,
                position_y DOUBLE PRECISION NOT NULL DEFAULT 0,
                z_index INT NOT NULL DEFAULT 0,
                rotation_deg REAL NOT NULL DEFAULT 0,
                decoration VARCHAR(1000) NULL DEFAULT '{}',
                body_image_url VARCHAR(1000) NULL,
                thumbnail_image_url VARCHAR(1000) NULL,
                attached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                report_count INT NOT NULL DEFAULT 0,
                is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
                moderation_status VARCHAR(32) NOT NULL DEFAULT 'pending',
                ocr_text VARCHAR(1000) NULL,
                ocr_categories VARCHAR(1000) NULL,
                moderation_checked_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP NULL,
                deleted_reason VARCHAR(32) NULL
            )
            """);
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS thumbnail_image_url VARCHAR(1000)");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_x DOUBLE PRECISION DEFAULT 0");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_y DOUBLE PRECISION DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS z_index INT DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS rotation_deg REAL DEFAULT 0");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS decoration VARCHAR(1000) DEFAULT '{}'");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS body_image_url VARCHAR(1000)");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS attached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS report_count INT DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT FALSE");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(32) DEFAULT 'pending'");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS ocr_text VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS ocr_categories VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS moderation_checked_at TIMESTAMP");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(32)");
    }

    private void cleanTables() {
        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM file_upload");
        jdbcTemplate.update("DELETE FROM artifact");
    }

    private UUID createExistingUser(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt);
        userRepository.saveAndFlush(appUser);

        return userUuid;
    }

    private UUID insertFileUpload(UUID userUuid, String objectKey, String purpose, String status,
        LocalDateTime deletedAt) {
        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO file_upload (
                id, user_id, purpose, original_file_name, content_type, byte_size, object_key, status, expires_at,
                created_at, updated_at, deleted_at
            )
            VALUES (?, ?, ?, 'memo.png', 'image/png', 1024, ?, ?, ?, ?, ?, ?)
            """, fileId, userUuid, purpose, objectKey, status, now.plusHours(1), now, now, deletedAt);

        return fileId;
    }

    private UUID insertDirectMemo(UUID userUuid, String originalImageUrl, String thumbnailImageUrl, int zIndex,
        LocalDateTime attachedAt, LocalDateTime deletedAt, boolean hidden) {
        UUID memoId = UUID.randomUUID();
        insertCommunityMemo(memoId, userUuid, null, originalImageUrl, thumbnailImageUrl, zIndex, attachedAt, deletedAt,
            hidden, "{}", 0, "pending", attachedAt, attachedAt);

        return memoId;
    }

    private void insertArtifact(UUID artifactId, String kind, String thumbnailUrl, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, NULL, ?, '{}', ?, ?)
            """, artifactId, kind, thumbnailUrl, createdAt, createdAt);
    }

    private void insertGallery(UUID galleryId, UUID userUuid, UUID artifactId, LocalDateTime deletedAt) {
        jdbcTemplate.update("INSERT INTO gallery (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, ?)", galleryId,
            userUuid, artifactId, deletedAt);
    }

    private void insertCommunityMemo(UUID memoId, UUID userUuid, UUID artifactId, String bodyImageUrl,
        String thumbnailImageUrl, int zIndex, LocalDateTime attachedAt, LocalDateTime deletedAt, boolean hidden,
        String decoration, int reportCount, String moderationStatus, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
            INSERT INTO community_memo (
                id, user_id, artifact_id, position_x, position_y, z_index, rotation_deg, body_image_url,
                thumbnail_image_url, attached_at, is_hidden, deleted_at, decoration, report_count, moderation_status,
                created_at, updated_at
            )
            VALUES (?, ?, ?, 120.5, 80.0, ?, -4.5, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, memoId, userUuid, artifactId, zIndex, bodyImageUrl, thumbnailImageUrl, attachedAt, hidden, deletedAt,
            decoration, reportCount, moderationStatus, createdAt, updatedAt);
    }

    private MockHttpServletRequestBuilder createRequest(UUID userUuid, String originalFileIdValue,
        String thumbnailFileIdValue) {
        String originalField = originalFileIdValue == null
            ? ""
            : "\"originalFileId\": \"%s\",".formatted(originalFileIdValue);
        String thumbnailField = thumbnailFileIdValue == null
            ? ""
            : "\"thumbnailFileId\": \"%s\",".formatted(thumbnailFileIdValue);

        return post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "sourceType": "DIRECT",
                  %s
                  %s
                  "positionX": 0.0,
                  "positionY": 0.0,
                  "zIndex": 1,
                  "rotationDeg": 0.0
                }
                """.formatted(originalField, thumbnailField));
    }

    private MockHttpServletRequestBuilder createRequestWithSource(UUID userUuid, UUID originalFileId,
        UUID thumbnailFileId, String sourceType, UUID sourceGalleryId) {
        String sourceGalleryField = sourceGalleryId == null
            ? ""
            : "\"sourceGalleryId\": \"%s\",".formatted(sourceGalleryId);

        return post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "sourceType": "%s",
                  "originalFileId": "%s",
                  "thumbnailFileId": "%s",
                  %s
                  "positionX": 0.0,
                  "positionY": 0.0,
                  "zIndex": 1,
                  "rotationDeg": 0.0
                }
                """.formatted(sourceType, originalFileId, thumbnailFileId, sourceGalleryField));
    }

    private MockHttpServletRequestBuilder createRequestWithDecoration(UUID userUuid, UUID originalFileId,
        UUID thumbnailFileId, String decoration) {
        return post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "sourceType": "DIRECT",
                  "originalFileId": "%s",
                  "thumbnailFileId": "%s",
                  "positionX": 0.0,
                  "positionY": 0.0,
                  "zIndex": 1,
                  "rotationDeg": 0.0,
                  "decoration": %s
                }
                """.formatted(originalFileId, thumbnailFileId, decoration));
    }

    private MockHttpServletRequestBuilder createGalleryRequest(UUID userUuid, String originalFileIdValue,
        String thumbnailFileIdValue, String sourceGalleryIdValue) {
        String sourceGalleryField = sourceGalleryIdValue == null
            ? ""
            : "\"sourceGalleryId\": \"%s\",".formatted(sourceGalleryIdValue);

        return post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "sourceType": "GALLERY",
                  %s
                  "originalFileId": "%s",
                  "thumbnailFileId": "%s",
                  "positionX": 0.0,
                  "positionY": 0.0,
                  "zIndex": 1,
                  "rotationDeg": 0.0
                }
                """.formatted(sourceGalleryField, originalFileIdValue, thumbnailFileIdValue));
    }
}
