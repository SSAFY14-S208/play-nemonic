package com.nemonicworld.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.artifact.service.download.ArtifactDownloadStorage;
import com.nemonicworld.artifact.service.download.ArtifactQrComposer;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationException;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationRequest;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationResult;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ExtendWith(OutputCaptureExtension.class)
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

    @MockitoBean
    private ArtifactDownloadStorage artifactDownloadStorage;

    @MockitoBean
    private ArtifactQrComposer artifactQrComposer;

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
        UUID secondMemoId = UUID.randomUUID();
        insertCommunityMemo(secondMemoId, userUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1,
            baseTime.plusMinutes(2), null, false, "{\"kind\":\"community-direct-v1\",\"memoColor\":\"#ffe887\"}", 0,
            "pending", baseTime.plusMinutes(2), baseTime.plusMinutes(2));
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
            .andExpect(jsonPath("$.data.items[1].memoPlaybackImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.items[1].memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[0].memoThumbnailImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.items[0].memoImageUrl")
                .value("http://localhost:9000/nemonic-local/fallback-original.png"))
            .andExpect(jsonPath("$.data.items[1].positionX").value(120.5))
            .andExpect(jsonPath("$.data.items[1].positionY").value(80.0))
            .andExpect(jsonPath("$.data.items[1].zIndex").value(1))
            .andExpect(jsonPath("$.data.items[1].rotationDeg").value(-4.5))
            .andExpect(jsonPath("$.data.items[1].ownedByMe").value(false))
            .andExpect(jsonPath("$.data.items[0].decoration", anEmptyMap()))
            .andExpect(jsonPath("$.data.items[1].decoration.kind").value("community-direct-v1"))
            .andExpect(jsonPath("$.data.items[1].decoration.memoColor").value("#ffe887"))
            .andExpect(jsonPath("$.data.items[1].userId").doesNotExist())
            .andExpect(jsonPath("$.data.items[1].authorUuid").doesNotExist());
    }

    @Test
    void getCommunityMemosReturnsFlipbookPlaybackUrlForVisibleGalleryMemoOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser("gif-owner");
        UUID viewerUuid = createExistingUser("gif-viewer");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID flipbookArtifactId = UUID.randomUUID();
        UUID relayArtifactId = UUID.randomUUID();
        UUID flipbookMemoId = UUID.randomUUID();
        UUID relayMemoId = UUID.randomUUID();

        insertArtifact(flipbookArtifactId, "flipbook", "flipbook-thumbnail.png", now);
        insertFlipbookArtifact(flipbookArtifactId, "flipbook/results/%s/result.gif".formatted(flipbookArtifactId),
            "flipbook/results/%s/first.png".formatted(flipbookArtifactId));
        insertArtifact(relayArtifactId, "relay_drawing", "relay-thumbnail.png", now);
        insertCommunityMemo(flipbookMemoId, ownerUuid, flipbookArtifactId, OBJECT_KEY_PREFIX + "flipbook-original.png",
            OBJECT_KEY_PREFIX + "flipbook-thumbnail.png", 1, now, null, false, "{}", 0, "allowed", now, now);
        insertCommunityMemo(relayMemoId, ownerUuid, relayArtifactId, OBJECT_KEY_PREFIX + "relay-original.png",
            OBJECT_KEY_PREFIX + "relay-thumbnail.png", 2, now.plusMinutes(1), null, false, "{}", 0, "allowed", now,
            now);

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(flipbookMemoId.toString()))
            .andExpect(jsonPath("$.data.items[0].sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.items[0].ownedByMe").value(false))
            .andExpect(jsonPath("$.data.items[0].memoPlaybackImageUrl").value(
                "http://localhost:9000/nemonic-local/flipbook/results/%s/result.gif".formatted(flipbookArtifactId)))
            .andExpect(jsonPath("$.data.items[1].memoUuid").value(relayMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.items[1].memoPlaybackImageUrl").value(nullValue()));
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
            .andExpect(jsonPath("$.data.memoPlaybackImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.decoration.frame").value("gold"))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"))
            .andExpect(jsonPath("$.data.reportCount").value(1)).andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.authorUuid").doesNotExist());
    }

    @Test
    void getCommunityMemoReturnsFlipbookPlaybackUrlForVisibleMemoOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser("gif-owner");
        UUID viewerUuid = createExistingUser("gif-viewer");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();
        String gifObjectKey = "flipbook/results/%s/result.gif".formatted(artifactId);

        insertArtifact(artifactId, "flipbook", "artifact-thumbnail-should-not-be-used.png", now);
        insertFlipbookArtifact(artifactId, gifObjectKey, "flipbook/results/%s/first.png".formatted(artifactId));
        insertCommunityMemo(memoId, ownerUuid, artifactId, OBJECT_KEY_PREFIX + "detail-original.png",
            OBJECT_KEY_PREFIX + "detail-thumbnail.png", 4, now, null, false, "{\"frame\":\"silver\"}", 0, "allowed",
            now, now);

        mockMvc
            .perform(get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.ownedByMe").value(false))
            .andExpect(jsonPath("$.data.galleryContentKind").value("flipbook"))
            .andExpect(
                jsonPath("$.data.memoPlaybackImageUrl").value("http://localhost:9000/nemonic-local/" + gifObjectKey))
            .andExpect(jsonPath("$.data.memoOriginalImageUrl").value(PUBLIC_URL_PREFIX + "detail-original.png"))
            .andExpect(jsonPath("$.data.memoThumbnailImageUrl").value(PUBLIC_URL_PREFIX + "detail-thumbnail.png"));
    }

    @Test
    void createCommunityMemoShareReturnsQrImageUrlForVisibleMemoOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser("share-own");
        UUID viewerUuid = createExistingUser("share-view");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = UUID.randomUUID();
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheObjectKey = "community-memo-shares/%s/result-qr.jpg".formatted(memoId);

        insertCommunityMemo(memoId, ownerUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1, now, null, false,
            "{}", 0, "allowed", now, now);
        when(artifactDownloadStorage.exists(cacheObjectKey)).thenReturn(false);
        when(artifactDownloadStorage.download(ORIGINAL_OBJECT_KEY)).thenReturn(sourceBytes);
        when(artifactQrComposer.compose(anyString(), any(), anyString())).thenReturn(composedBytes);

        mockMvc
            .perform(post("/api/v1/community/memos/{memoUuid}/share", memoId).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 공유 정보 생성 성공"))
            .andExpect(jsonPath("$.data.shareToken").isNotEmpty())
            .andExpect(jsonPath("$.data.imageUrl")
                .value("http://localhost:9000/nemonic-local/community-memo-shares/%s/result-qr.jpg".formatted(memoId)))
            .andExpect(jsonPath("$.data.siteUrl").value("http://localhost:3000"))
            .andExpect(jsonPath("$.data.kakaoUrl").value(containsString("utm_campaign=community_memo_result")))
            .andExpect(jsonPath("$.data.instagramUrl").value(containsString("utm_medium=story")));

        verify(artifactDownloadStorage).download(ORIGINAL_OBJECT_KEY);
        verify(artifactDownloadStorage).upload(cacheObjectKey, composedBytes, "image/jpeg");
        verify(artifactQrComposer).compose(anyString(), any(), anyString());
    }

    @Test
    void createCommunityMemoShareFallsBackToThumbnailImage() throws Exception {
        UUID userUuid = createExistingUser("share-thum");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = UUID.randomUUID();
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheObjectKey = "community-memo-shares/%s/result-qr.jpg".formatted(memoId);

        insertCommunityMemo(memoId, userUuid, null, null, THUMBNAIL_OBJECT_KEY, 1, now, null, false, "{}", 0, "allowed",
            now, now);
        when(artifactDownloadStorage.exists(cacheObjectKey)).thenReturn(false);
        when(artifactDownloadStorage.download(THUMBNAIL_OBJECT_KEY)).thenReturn(sourceBytes);
        when(artifactQrComposer.compose(anyString(), any(), anyString())).thenReturn(composedBytes);

        mockMvc
            .perform(post("/api/v1/community/memos/{memoUuid}/share", memoId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.imageUrl")
                .value("http://localhost:9000/nemonic-local/community-memo-shares/%s/result-qr.jpg".formatted(memoId)));

        verify(artifactDownloadStorage).download(THUMBNAIL_OBJECT_KEY);
    }

    @Test
    void createCommunityMemoShareRejectsHiddenDeletedAndBlockedMemos() throws Exception {
        UUID ownerUuid = createExistingUser("share-off");
        UUID viewerUuid = createExistingUser("share-view");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID deletedMemoId = UUID.randomUUID();
        UUID hiddenMemoId = UUID.randomUUID();
        UUID blockedMemoId = UUID.randomUUID();

        insertCommunityMemo(deletedMemoId, ownerUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1, now, now,
            false, "{}", 0, "allowed", now, now);
        insertCommunityMemo(hiddenMemoId, ownerUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 2, now, null,
            true, "{}", 0, "allowed", now, now);
        insertCommunityMemo(blockedMemoId, ownerUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 3, now, null,
            false, "{}", 0, "blocked", now, now);

        mockMvc.perform(shareRequest(deletedMemoId, viewerUuid)).andExpect(status().isNotFound());
        mockMvc.perform(shareRequest(hiddenMemoId, viewerUuid)).andExpect(status().isNotFound());
        mockMvc.perform(shareRequest(blockedMemoId, viewerUuid)).andExpect(status().isBadRequest());

        verifyNoInteractions(artifactDownloadStorage, artifactQrComposer);
    }

    @Test
    void getCommunityMemosReturnsDecorationConsistentWithDetail() throws Exception {
        UUID userUuid = createExistingUser("color-user");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = UUID.randomUUID();
        String decoration = "{\"kind\":\"community-direct-v1\",\"memoColor\":\"#d7f3ff\"}";

        insertCommunityMemo(memoId, userUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1, now, null, false,
            decoration, 0, "allowed", now, now);

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.items[0].decoration.kind").value("community-direct-v1"))
            .andExpect(jsonPath("$.data.items[0].decoration.memoColor").value("#d7f3ff"));

        mockMvc
            .perform(
                get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.decoration.kind").value("community-direct-v1"))
            .andExpect(jsonPath("$.data.decoration.memoColor").value("#d7f3ff"));
    }

    @Test
    void getCommunityMemosReturnsEmptyDecorationForEmptyOrInvalidDecoration() throws Exception {
        UUID userUuid = createExistingUser("deco-empty");
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID emptyDecorationMemoId = UUID.randomUUID();
        UUID invalidDecorationMemoId = UUID.randomUUID();

        insertCommunityMemo(emptyDecorationMemoId, userUuid, null, "empty-decoration-original.png",
            "empty-decoration-thumbnail.png", 1, baseTime.plusMinutes(1), null, false, "{}", 0, "allowed",
            baseTime.plusMinutes(1), baseTime.plusMinutes(1));
        insertCommunityMemo(invalidDecorationMemoId, userUuid, null, "invalid-decoration-original.png",
            "invalid-decoration-thumbnail.png", 2, baseTime.plusMinutes(2), null, false, "{invalid-json", 0, "allowed",
            baseTime.plusMinutes(2), baseTime.plusMinutes(2));

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(emptyDecorationMemoId.toString()))
            .andExpect(jsonPath("$.data.items[0].decoration", anEmptyMap()))
            .andExpect(jsonPath("$.data.items[1].memoUuid").value(invalidDecorationMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].decoration", anEmptyMap()));
    }

    @Test
    void createCommunityMemoCreatesDirectSnapshotMemoAfterModerationAllowed(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser("생성메모");
        UUID originalFileId = insertFileUpload(userUuid, ORIGINAL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, THUMBNAIL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        when(moderationClient.check(any())).thenReturn(new CommunityMemoModerationResult(true, "추출 텍스트",
            objectMapper.readTree("[{\"name\":\"abuse\",\"score\":0.01}]")));

        MvcResult createResult = mockMvc
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
            .andExpect(jsonPath("$.data.reportCount").value(0)).andReturn();

        UUID memoId = createdMemoId(createResult);
        assertThat(memoId).isNotNull();
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(ORIGINAL_OBJECT_KEY);
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

        assertThat(output).contains("\"event_name\":\"community_memo_created\"")
            .contains("\"event_name\":\"community_memo_moderation_allowed\"").contains("body_image_object_key_hash")
            .contains("thumbnail_image_object_key_hash").contains("client_text_length").contains("checked_text_length")
            .doesNotContain("텍스트박스 원문").doesNotContain("추출 텍스트").doesNotContain(ORIGINAL_OBJECT_KEY)
            .doesNotContain(THUMBNAIL_OBJECT_KEY).doesNotContain("client_text_preview")
            .doesNotContain("ocr_text_preview");
    }

    @Test
    void createCommunityMemoStoresEmptyDecorationAndEmptyClientTextWhenOmitted() throws Exception {
        UUID userUuid = createExistingUser("기본값");
        String originalObjectKey = "uploads/community/no-decoration-original.png";
        String thumbnailObjectKey = "uploads/community/no-decoration-thumbnail.png";
        UUID originalFileId = insertFileUpload(userUuid, originalObjectKey, "COMMUNITY", "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, thumbnailObjectKey, "COMMUNITY", "UPLOADED", null);

        MvcResult createResult = mockMvc
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
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.decoration").value(anEmptyMap())).andReturn();

        UUID memoId = createdMemoId(createResult);
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(originalObjectKey);
        assertThat(
            jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("{}");
        ArgumentCaptor<CommunityMemoModerationRequest> requestCaptor = ArgumentCaptor
            .forClass(CommunityMemoModerationRequest.class);
        verify(moderationClient).check(requestCaptor.capture());
        assertThat(requestCaptor.getValue().thumbnailUrl()).isEqualTo(publicUrl(thumbnailObjectKey));
        assertThat(requestCaptor.getValue().clientText()).isEmpty();
    }

    @Test
    void createCommunityMemoCreatesGallerySnapshotMemoWithSourceArtifact() throws Exception {
        UUID userUuid = createExistingUser("갤러리게시");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        String originalObjectKey = OBJECT_KEY_PREFIX + "posted-original.png";
        String thumbnailObjectKey = OBJECT_KEY_PREFIX + "posted-thumbnail.png";
        String playbackObjectKey = "flipbook/results/%s/result.gif".formatted(artifactId);
        UUID originalFileId = insertFileUpload(userUuid, originalObjectKey, "COMMUNITY", "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, thumbnailObjectKey, "COMMUNITY", "UPLOADED", null);
        insertArtifact(artifactId, "flipbook", "artifact-thumbnail-should-not-render.png", now);
        insertFlipbookArtifact(artifactId, playbackObjectKey, "flipbook/results/%s/first.png".formatted(artifactId));
        insertGallery(galleryId, userUuid, artifactId, null);
        when(moderationClient.check(any()))
            .thenReturn(new CommunityMemoModerationResult(true, "갤러리 OCR", objectMapper.readTree("[\"safe\"]")));

        MvcResult createResult = mockMvc
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
            .andExpect(jsonPath("$.data.memoPlaybackImageUrl")
                .value("http://localhost:9000/nemonic-local/" + playbackObjectKey))
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed")).andReturn();

        UUID memoId = createdMemoId(createResult);
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isEqualTo(artifactId);
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(originalObjectKey);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(thumbnailObjectKey);
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

        MvcResult createResult = mockMvc
            .perform(createRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.memoImageUrl").value(publicUrl("uploads/community/fifo-new-thumbnail.png")))
            .andReturn();
        UUID memoId = createdMemoId(createResult);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class,
            oldestMemoId)).isEqualTo("expired");
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("uploads/community/fifo-new-original.png");
    }

    @Test
    void createCommunityMemoUsesCommunityMaxMemoCountSettingForFifoLimit() throws Exception {
        insertCommunityMaxMemoCountSetting(3);
        UUID userUuid = createExistingUser("설정FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        UUID oldestMemoId = null;
        for (int index = 0; index < 3; index++) {
            UUID memoId = insertDirectMemo(userUuid, "dynamic-fifo-original-%02d.png".formatted(index),
                "dynamic-fifo-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
            if (index == 0) {
                oldestMemoId = memoId;
            }
        }
        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/dynamic-fifo-new-original.png", "COMMUNITY",
            "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/dynamic-fifo-new-thumbnail.png",
            "COMMUNITY", "UPLOADED", null);

        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString()))
            .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class,
            oldestMemoId)).isEqualTo("expired");
    }

    @Test
    void communityMaxMemoCountSettingChangeAppliesFromNextCreateWithoutImmediateExpiry() throws Exception {
        insertCommunityMaxMemoCountSetting(5);
        UUID userUuid = createExistingUser("설정변경FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        for (int index = 0; index < 5; index++) {
            insertDirectMemo(userUuid, "setting-change-original-%02d.png".formatted(index),
                "setting-change-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
        }

        updateCommunityMaxMemoCountSetting(3);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(5);

        UUID originalFileId = insertFileUpload(userUuid, "uploads/community/setting-change-new-original.png",
            "COMMUNITY", "UPLOADED", null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "uploads/community/setting-change-new-thumbnail.png",
            "COMMUNITY", "UPLOADED", null);

        mockMvc.perform(createRequest(userUuid, originalFileId.toString(), thumbnailFileId.toString()))
            .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo WHERE deleted_reason = 'expired'",
            Integer.class)).isEqualTo(3);
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
    void updateCommunityMemoLayoutUpdatesOnlyOwnedVisibleMemoLayout() throws Exception {
        UUID userUuid = createExistingUser("배치수정");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();
        insertArtifact(artifactId, "relay_drawing", "artifact-thumbnail.png", baseTime);
        insertCommunityMemo(memoId, userUuid, artifactId, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 3, baseTime, null,
            false, "{\"scale\":1.0}", 2, "allowed", baseTime.minusMinutes(1), baseTime.minusMinutes(1));

        clearInvocations(moderationClient);
        mockMvc
            .perform(patch("/api/v1/community/memos/{memoId}", memoId)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "positionX": 120.5,
                      "positionY": -30.0,
                      "zIndex": 12,
                      "rotationDeg": 5.5,
                      "decoration": {
                        "scale": 9.9
                      }
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 수정 성공"))
            .andExpect(jsonPath("$.data.memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.memoOriginalImageUrl").value(ORIGINAL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.memoThumbnailImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.positionX").value(120.5)).andExpect(jsonPath("$.data.positionY").value(-30.0))
            .andExpect(jsonPath("$.data.zIndex").value(12)).andExpect(jsonPath("$.data.rotationDeg").value(5.5))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"))
            .andExpect(jsonPath("$.data.reportCount").value(2));
        verifyNoInteractions(moderationClient);

        assertThat(
            jdbcTemplate.queryForObject("SELECT position_x FROM community_memo WHERE id = ?", Double.class, memoId))
            .isEqualTo(120.5);
        assertThat(
            jdbcTemplate.queryForObject("SELECT position_y FROM community_memo WHERE id = ?", Double.class, memoId))
            .isEqualTo(-30.0);
        assertThat(
            jdbcTemplate.queryForObject("SELECT z_index FROM community_memo WHERE id = ?", Integer.class, memoId))
            .isEqualTo(12);
        assertThat(
            jdbcTemplate.queryForObject("SELECT rotation_deg FROM community_memo WHERE id = ?", Float.class, memoId))
            .isEqualTo(5.5f);
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(ORIGINAL_OBJECT_KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(THUMBNAIL_OBJECT_KEY);
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isEqualTo(artifactId);
        assertThat(
            jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("{\"scale\":1.0}");
        assertThat(jdbcTemplate.queryForObject("SELECT attached_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime);
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isAfter(baseTime.minusMinutes(1));
    }

    @Test
    void updateCommunityMemoLayoutRejectsUnauthorizedMissingDeletedAndHiddenMemos() throws Exception {
        UUID ownerUuid = createExistingUser("소유자");
        UUID otherUserUuid = createExistingUser("타인");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID visibleMemoId = insertDirectMemo(ownerUuid, "owned-original.png", "owned-thumbnail.png", 1, now, null,
            false);
        UUID deletedMemoId = insertDirectMemo(ownerUuid, "deleted-update-original.png", "deleted-update-thumbnail.png",
            1, now, now, false);
        UUID hiddenMemoId = insertDirectMemo(ownerUuid, "hidden-update-original.png", "hidden-update-thumbnail.png", 1,
            now, null, true);

        mockMvc.perform(updateRequest(visibleMemoId, otherUserUuid.toString())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치를 수정할 권한이 없습니다."));
        mockMvc.perform(updateRequest(UUID.randomUUID(), ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(updateRequest(deletedMemoId, ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(updateRequest(hiddenMemoId, ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(updateRequest(visibleMemoId, UUID.randomUUID().toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
        mockMvc.perform(updateRequest(visibleMemoId, null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc.perform(updateRequest(visibleMemoId, "not-a-uuid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(
                patch("/api/v1/community/memos/not-a-uuid").header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString())
                    .contentType(MediaType.APPLICATION_JSON).content(validLayoutJson()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    @Test
    void updateCommunityMemoLayoutValidatesRequiredAndFinitePositionFields() throws Exception {
        UUID userUuid = createExistingUser("검증");
        UUID memoId = insertDirectMemo(userUuid, "validation-original.png", "validation-thumbnail.png", 1,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null, false);

        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionY": 0.0,
              "zIndex": 1,
              "rotationDeg": 0.0
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionX": 0.0,
              "zIndex": 1,
              "rotationDeg": 0.0
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionX": 0.0,
              "positionY": 0.0,
              "rotationDeg": 0.0
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionX": 0.0,
              "positionY": 0.0,
              "zIndex": 1
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionX": 1e309,
              "positionY": 0.0,
              "zIndex": 1,
              "rotationDeg": 0.0
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
        mockMvc.perform(updateRequest(memoId, userUuid.toString(), """
            {
              "positionX": 0.0,
              "positionY": 0.0,
              "zIndex": 1,
              "rotationDeg": 4e38
            }
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
    }

    @Test
    void updateCommunityMemoLayoutDoesNotRunFifoWhenVisibleCountAlreadyExceedsLimit() throws Exception {
        UUID userUuid = createExistingUser("수정FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        UUID updateTargetMemoId = null;
        for (int index = 0; index < 51; index++) {
            UUID memoId = insertDirectMemo(userUuid, "update-fifo-original-%02d.png".formatted(index),
                "update-fifo-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
            if (index == 50) {
                updateTargetMemoId = memoId;
            }
        }

        mockMvc.perform(updateRequest(updateTargetMemoId, userUuid.toString())).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(51);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo WHERE deleted_reason = 'expired'",
            Integer.class)).isZero();
    }

    @Test
    void deleteCommunityMemoSoftDeletesOwnedVisibleMemoAndPreservesRelatedData() throws Exception {
        UUID userUuid = createExistingUser("삭제메모");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();

        insertArtifact(artifactId, "relay_drawing", "artifact-thumbnail.png", baseTime);
        insertGallery(galleryId, userUuid, artifactId, null);
        insertFileUpload(userUuid, ORIGINAL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        insertFileUpload(userUuid, THUMBNAIL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        insertCommunityMemo(memoId, userUuid, artifactId, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 3, baseTime, null,
            false, "{\"scale\":1.0}", 2, "allowed", baseTime.minusMinutes(1), baseTime.minusMinutes(1));
        jdbcTemplate.update("""
            UPDATE community_memo
            SET ocr_text = '검수 텍스트',
                ocr_categories = '[\"safe\"]',
                moderation_checked_at = ?
            WHERE id = ?
            """, baseTime.plusMinutes(1), memoId);

        clearInvocations(moderationClient);
        mockMvc.perform(deleteRequest(memoId, userUuid.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("커뮤니티 메모 삭제 성공"))
            .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(moderationClient);

        LocalDateTime deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId);
        LocalDateTime updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId);
        assertThat(deletedAt).isNotNull();
        assertThat(updatedAt).isEqualTo(deletedAt);
        assertThat(
            jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("user_delete");
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(ORIGINAL_OBJECT_KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(THUMBNAIL_OBJECT_KEY);
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isEqualTo(artifactId);
        assertThat(
            jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("{\"scale\":1.0}");
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo("allowed");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_text FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("검수 텍스트");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_categories FROM community_memo WHERE id = ?", String.class, memoId))
            .contains("safe");
        assertThat(jdbcTemplate.queryForObject("SELECT attached_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime);
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime.minusMinutes(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_upload WHERE object_key IN (?, ?)",
            Integer.class, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gallery WHERE id = ?", Integer.class, galleryId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artifact WHERE id = ?", Integer.class, artifactId))
            .isEqualTo(1);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isNotFound());
        mockMvc.perform(updateRequest(memoId, userUuid.toString())).andExpect(status().isNotFound());
    }

    @Test
    void deleteCommunityMemoRejectsUnauthorizedMissingDeletedAndHiddenMemos() throws Exception {
        UUID ownerUuid = createExistingUser("삭제소유자");
        UUID otherUserUuid = createExistingUser("삭제타인");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID visibleMemoId = insertDirectMemo(ownerUuid, "delete-owned-original.png", "delete-owned-thumbnail.png", 1,
            now, null, false);
        UUID deletedMemoId = insertDirectMemo(ownerUuid, "already-deleted-original.png",
            "already-deleted-thumbnail.png", 1, now, now, false);
        UUID hiddenMemoId = insertDirectMemo(ownerUuid, "hidden-delete-original.png", "hidden-delete-thumbnail.png", 1,
            now, null, true);

        mockMvc.perform(deleteRequest(visibleMemoId, otherUserUuid.toString())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모를 삭제할 권한이 없습니다."));
        mockMvc.perform(deleteRequest(UUID.randomUUID(), ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(deleteRequest(deletedMemoId, ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(deleteRequest(hiddenMemoId, ownerUuid.toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(deleteRequest(visibleMemoId, UUID.randomUUID().toString())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
        mockMvc.perform(deleteRequest(visibleMemoId, null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc.perform(deleteRequest(visibleMemoId, "not-a-uuid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(
                delete("/api/v1/community/memos/not-a-uuid").header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    @Test
    void deleteCommunityMemoDoesNotRunFifoOrRestoreExpiredMemos() throws Exception {
        UUID userUuid = createExistingUser("삭제FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        UUID deleteTargetMemoId = null;
        for (int index = 0; index < 50; index++) {
            UUID memoId = insertDirectMemo(userUuid, "delete-fifo-original-%02d.png".formatted(index),
                "delete-fifo-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
            if (index == 49) {
                deleteTargetMemoId = memoId;
            }
        }
        UUID expiredMemoId = insertDirectMemo(userUuid, "expired-restore-original.png", "expired-restore-thumbnail.png",
            1, baseTime.minusMinutes(1), baseTime, false);
        jdbcTemplate.update("UPDATE community_memo SET deleted_reason = 'expired' WHERE id = ?", expiredMemoId);

        clearInvocations(moderationClient);
        mockMvc.perform(deleteRequest(deleteTargetMemoId, userUuid.toString())).andExpect(status().isOk());
        verifyNoInteractions(moderationClient);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(49);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class,
            expiredMemoId)).isEqualTo("expired");
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, expiredMemoId)).isNotNull();
    }

    @Test
    void reportCommunityMemoCreatesReportAndIncrementsCount() throws Exception {
        UUID ownerUuid = createExistingUser("신고대상");
        UUID reporterUuid = createExistingUser("신고자");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertDirectMemo(ownerUuid, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 1, now, null, false);

        clearInvocations(moderationClient);
        mockMvc.perform(reportRequest(memoId, reporterUuid.toString(), reportJson("욕설/비방/혐오", "욕설이 포함되어 있어요.")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 성공"))
            .andExpect(jsonPath("$.data.memoId").value(memoId.toString()))
            .andExpect(jsonPath("$.data.reportCount").value(1)).andExpect(jsonPath("$.data.hidden").value(false));
        verifyNoInteractions(moderationClient);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo_report
            WHERE memo_id = ?
              AND user_id = ?
              AND reason = 'abuse_hate'
            """, Integer.class, memoId, reporterUuid)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT reason_detail
            FROM community_memo_report
            WHERE memo_id = ?
              AND user_id = ?
            """, String.class, memoId, reporterUuid)).isEqualTo("욕설이 포함되어 있어요.");
        assertThat(
            jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class, memoId))
            .isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class, memoId))
            .isFalse();
    }

    @Test
    void reportCommunityMemoUsesCommunityReportHideThresholdSetting() throws Exception {
        insertCommunityReportHideThresholdSetting(3);
        UUID ownerUuid = createExistingUser("자동숨김");
        UUID reporterUuid = createExistingUser("세번째");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();

        insertArtifact(artifactId, "relay_drawing", "artifact-thumbnail.png", baseTime);
        insertGallery(galleryId, ownerUuid, artifactId, null);
        insertFileUpload(ownerUuid, ORIGINAL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        insertFileUpload(ownerUuid, THUMBNAIL_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);
        insertCommunityMemo(memoId, ownerUuid, artifactId, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, 3, baseTime, null,
            false, "{\"scale\":1.0}", 2, "allowed", baseTime.minusMinutes(1), baseTime.minusMinutes(1));
        jdbcTemplate.update("""
            UPDATE community_memo
            SET ocr_text = '신고 전 OCR',
                ocr_categories = '[\"safe\"]',
                moderation_checked_at = ?
            WHERE id = ?
            """, baseTime.plusMinutes(1), memoId);
        for (int index = 0; index < 2; index++) {
            insertMemoReport(memoId, createExistingUser("기존신고" + index), "spam", baseTime.plusSeconds(index));
        }

        clearInvocations(moderationClient);
        mockMvc.perform(reportRequest(memoId, reporterUuid.toString(), validReportJson("기타")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.reportCount").value(3))
            .andExpect(jsonPath("$.data.hidden").value(true));
        verifyNoInteractions(moderationClient);

        LocalDateTime hiddenAt = jdbcTemplate.queryForObject("SELECT hidden_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId);
        LocalDateTime updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId);
        assertThat(hiddenAt).isNotNull();
        assertThat(updatedAt).isEqualTo(hiddenAt);
        assertThat(
            jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class, memoId))
            .isEqualTo(3);
        assertThat(
            jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class, memoId))
            .isTrue();
        assertThat(
            jdbcTemplate.queryForObject("SELECT hidden_reason FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("report_threshold");
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(ORIGINAL_OBJECT_KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(THUMBNAIL_OBJECT_KEY);
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isEqualTo(artifactId);
        assertThat(
            jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("{\"scale\":1.0}");
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo("allowed");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_text FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("신고 전 OCR");
        assertThat(
            jdbcTemplate.queryForObject("SELECT ocr_categories FROM community_memo WHERE id = ?", String.class, memoId))
            .contains("safe");
        assertThat(jdbcTemplate.queryForObject("SELECT attached_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime);
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime.minusMinutes(1));
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_upload WHERE object_key IN (?, ?)",
            Integer.class, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gallery WHERE id = ?", Integer.class, galleryId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artifact WHERE id = ?", Integer.class, artifactId))
            .isEqualTo(1);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isNotFound());
        mockMvc.perform(updateRequest(memoId, ownerUuid.toString())).andExpect(status().isNotFound());
        mockMvc.perform(deleteRequest(memoId, ownerUuid.toString())).andExpect(status().isNotFound());
        mockMvc.perform(reportRequest(memoId, createExistingUser("숨김후신고").toString(), validReportJson("스팸/광고")))
            .andExpect(status().isNotFound());
    }

    @Test
    void reportCommunityMemoRejectsDuplicateOwnMissingDeletedHiddenAndInvalidUuid() throws Exception {
        UUID ownerUuid = createExistingUser("신고소유자");
        UUID reporterUuid = createExistingUser("중복신고자");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID visibleMemoId = insertDirectMemo(ownerUuid, "report-owned-original.png", "report-owned-thumbnail.png", 1,
            now, null, false);
        UUID deletedMemoId = insertDirectMemo(ownerUuid, "report-deleted-original.png", "report-deleted-thumbnail.png",
            1, now, now, false);
        UUID hiddenMemoId = insertDirectMemo(ownerUuid, "report-hidden-original.png", "report-hidden-thumbnail.png", 1,
            now, null, true);
        insertMemoReport(visibleMemoId, reporterUuid, "spam", now);

        mockMvc.perform(reportRequest(visibleMemoId, reporterUuid.toString(), validReportJson("스팸/광고")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 신고한 커뮤니티 메모입니다."));
        mockMvc.perform(reportRequest(visibleMemoId, ownerUuid.toString(), validReportJson("부적절한 콘텐츠")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("본인 메모는 신고할 수 없습니다."));
        mockMvc.perform(reportRequest(UUID.randomUUID(), reporterUuid.toString(), validReportJson("기타")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(reportRequest(deletedMemoId, reporterUuid.toString(), validReportJson("기타")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(reportRequest(hiddenMemoId, reporterUuid.toString(), validReportJson("기타")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(reportRequest(visibleMemoId, UUID.randomUUID().toString(), validReportJson("기타")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
        mockMvc.perform(reportRequest(visibleMemoId, null, validReportJson("기타"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc.perform(reportRequest(visibleMemoId, "not-a-uuid", validReportJson("기타")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos/not-a-uuid/reports")
                .header(ANONYMOUS_USER_UUID_HEADER, reporterUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(validReportJson("기타")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    @Test
    void reportCommunityMemoValidatesAndAcceptsReportReasonValues() throws Exception {
        UUID ownerUuid = createExistingUser("사유대상");
        UUID reporterUuid = createExistingUser("사유신고자");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String[][] allowedReasons = {{"부적절한 콘텐츠", "inappropriate"}, {"욕설/비방/혐오", "abuse_hate"},
            {"선정적/음란물", "sexual_content"}, {"폭력적/위협적 표현", "violence_threat"}, {"스팸/광고", "spam"},
            {"개인정보 노출", "personal_info"}, {"도용/사칭", "impersonation"}, {"기타", "other"}};
        UUID invalidMemoId = insertDirectMemo(ownerUuid, "invalid-original.png", "invalid-thumbnail.png", 1,
            now.plusMinutes(2), null, false);

        for (int index = 0; index < allowedReasons.length; index++) {
            UUID memoId = insertDirectMemo(ownerUuid, "reason-original-%02d.png".formatted(index),
                "reason-thumbnail-%02d.png".formatted(index), 1, now.plusMinutes(index), null, false);

            mockMvc.perform(reportRequest(memoId, reporterUuid.toString(), validReportJson(allowedReasons[index][0])))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.reportCount").value(1));
            assertThat(jdbcTemplate.queryForObject("SELECT reason FROM community_memo_report WHERE memo_id = ?",
                String.class, memoId)).isEqualTo(allowedReasons[index][1]);
        }
        mockMvc.perform(reportRequest(invalidMemoId, reporterUuid.toString(), "{}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 사유가 올바르지 않습니다."));
        mockMvc.perform(reportRequest(invalidMemoId, reporterUuid.toString(), validReportJson("INAPPROPRIATE")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 사유가 올바르지 않습니다."));
        mockMvc.perform(reportRequest(invalidMemoId, reporterUuid.toString(), validReportJson("inappropriate")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 사유가 올바르지 않습니다."));
        mockMvc.perform(reportRequest(invalidMemoId, reporterUuid.toString(), validReportJson("unknown")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 사유가 올바르지 않습니다."));
    }

    @Test
    void reportCommunityMemoDoesNotRunFifoOrRestoreExpiredMemos() throws Exception {
        UUID ownerUuid = createExistingUser("신고FIFO");
        UUID reporterUuid = createExistingUser("신고자FIFO");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        UUID reportTargetMemoId = null;
        for (int index = 0; index < 50; index++) {
            UUID memoId = insertDirectMemo(ownerUuid, "report-fifo-original-%02d.png".formatted(index),
                "report-fifo-thumbnail-%02d.png".formatted(index), 1, baseTime.plusMinutes(index), null, false);
            if (index == 49) {
                reportTargetMemoId = memoId;
            }
        }
        UUID expiredMemoId = insertDirectMemo(ownerUuid, "report-expired-original.png", "report-expired-thumbnail.png",
            1, baseTime.minusMinutes(1), baseTime, false);
        jdbcTemplate.update("UPDATE community_memo SET deleted_reason = 'expired' WHERE id = ?", expiredMemoId);

        clearInvocations(moderationClient);
        mockMvc.perform(reportRequest(reportTargetMemoId, reporterUuid.toString(), validReportJson("기타")))
            .andExpect(status().isCreated());
        verifyNoInteractions(moderationClient);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_reason FROM community_memo WHERE id = ?", String.class,
            expiredMemoId)).isEqualTo("expired");
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, expiredMemoId)).isNotNull();
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
            CREATE TABLE IF NOT EXISTS admin_user (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                login_id VARCHAR(64) NOT NULL DEFAULT '',
                password_hash VARCHAR(255) NOT NULL DEFAULT '',
                nickname VARCHAR(20) NOT NULL,
                email VARCHAR(255) NOT NULL DEFAULT '',
                role VARCHAR(32) NOT NULL DEFAULT 'admin',
                last_login_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS backoffice_setting (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                setting_key VARCHAR(128) NOT NULL UNIQUE,
                setting_value TEXT NOT NULL DEFAULT '{}',
                updated_by BIGINT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
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
            CREATE TABLE IF NOT EXISTS fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL DEFAULT '',
                fortune_image_url VARCHAR(1000) NOT NULL DEFAULT ''
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS fortune_artifact_asset (
                id BIGINT PRIMARY KEY,
                artifact_id UUID NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS flipbook_artifact (
                artifact_id UUID PRIMARY KEY,
                gif_url VARCHAR(1000) NOT NULL DEFAULT '',
                first_image VARCHAR(1000) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS infinite_canvas_artifact (
                artifact_id UUID PRIMARY KEY,
                canvas_image_url VARCHAR(1000) NOT NULL DEFAULT ''
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS phone_artifact (
                artifact_id UUID PRIMARY KEY,
                phone_image_url VARCHAR(1000) NOT NULL DEFAULT ''
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(1000) NULL
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
                hidden_reason VARCHAR(32) NULL,
                hidden_at TIMESTAMP NULL,
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
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP");
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
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS community_memo_report (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                memo_id UUID NOT NULL,
                user_id UUID NOT NULL,
                reason VARCHAR(32) NOT NULL,
                reason_detail VARCHAR(1000) NULL,
                created_at TIMESTAMP NOT NULL,
                CONSTRAINT uq_community_memo_report_memo_user UNIQUE (memo_id, user_id)
            )
            """);
    }

    private void cleanTables() {
        jdbcTemplate.update("DELETE FROM backoffice_setting");
        jdbcTemplate.update("DELETE FROM community_memo_report");
        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM file_upload");
        jdbcTemplate.update("DELETE FROM fortune_artifact_asset");
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM flipbook_artifact");
        jdbcTemplate.update("DELETE FROM infinite_canvas_artifact");
        jdbcTemplate.update("DELETE FROM phone_artifact");
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
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

    private void insertCommunityMaxMemoCountSetting(int maxMemoCount) {
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO backoffice_setting (
                id,
                setting_key,
                setting_value,
                updated_by,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """, 10L, "community.max_memo_count",
            "{\"value\":%d,\"unit\":\"count\",\"description\":\"커뮤니티 캔버스 표시 메모 수 제한\"}".formatted(maxMemoCount), 0L,
            now, now);
    }

    private void insertCommunityReportHideThresholdSetting(int threshold) {
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO backoffice_setting (
                id,
                setting_key,
                setting_value,
                updated_by,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """, 11L, "community.report_hide_threshold",
            "{\"value\":%d,\"unit\":\"count\",\"description\":\"커뮤니티 메모 자동 숨김 신고 기준\"}".formatted(threshold), 0L, now,
            now);
    }

    private void updateCommunityMaxMemoCountSetting(int maxMemoCount) {
        jdbcTemplate.update("""
            UPDATE backoffice_setting
            SET setting_value = ?,
                updated_at = ?
            WHERE setting_key = ?
            """, "{\"value\":%d,\"unit\":\"count\",\"description\":\"커뮤니티 캔버스 표시 메모 수 제한\"}".formatted(maxMemoCount),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), "community.max_memo_count");
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
            """, new Object[]{artifactId, kind, thumbnailUrl, createdAt, createdAt},
            new int[]{Types.OTHER, Types.OTHER, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP});
    }

    private void insertFlipbookArtifact(UUID artifactId, String gifUrl, String firstImageUrl) {
        jdbcTemplate.update("INSERT INTO flipbook_artifact (artifact_id, gif_url, first_image) VALUES (?, ?, ?)",
            artifactId, gifUrl, firstImageUrl);
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
            """,
            new Object[]{memoId, userUuid, artifactId, zIndex, bodyImageUrl, thumbnailImageUrl, attachedAt, hidden,
                deletedAt, decoration, reportCount, moderationStatus, createdAt, updatedAt},
            new int[]{Types.OTHER, Types.OTHER, Types.OTHER, Types.INTEGER, Types.VARCHAR, Types.VARCHAR,
                Types.TIMESTAMP, Types.BOOLEAN, Types.TIMESTAMP, Types.VARCHAR, Types.INTEGER, Types.OTHER,
                Types.TIMESTAMP, Types.TIMESTAMP});
    }

    private void insertMemoReport(UUID memoId, UUID userUuid, String reason, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO community_memo_report (memo_id, user_id, reason, reason_detail, created_at)
            VALUES (?, ?, ?, NULL, ?)
            """, new Object[]{memoId, userUuid, reason, createdAt},
            new int[]{Types.OTHER, Types.OTHER, Types.OTHER, Types.TIMESTAMP});
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

    private MockHttpServletRequestBuilder updateRequest(UUID memoId, String userUuidValue) {
        return updateRequest(memoId, userUuidValue, validLayoutJson());
    }

    private MockHttpServletRequestBuilder updateRequest(UUID memoId, String userUuidValue, String content) {
        MockHttpServletRequestBuilder request = patch("/api/v1/community/memos/{memoId}", memoId)
            .contentType(MediaType.APPLICATION_JSON).content(content);
        if (userUuidValue != null) {
            request.header(ANONYMOUS_USER_UUID_HEADER, userUuidValue);
        }

        return request;
    }

    private MockHttpServletRequestBuilder deleteRequest(UUID memoId, String userUuidValue) {
        MockHttpServletRequestBuilder request = delete("/api/v1/community/memos/{memoId}", memoId);
        if (userUuidValue != null) {
            request.header(ANONYMOUS_USER_UUID_HEADER, userUuidValue);
        }

        return request;
    }

    private MockHttpServletRequestBuilder reportRequest(UUID memoId, String userUuidValue, String content) {
        MockHttpServletRequestBuilder request = post("/api/v1/community/memos/{memoId}/reports", memoId)
            .contentType(MediaType.APPLICATION_JSON).content(content);
        if (userUuidValue != null) {
            request.header(ANONYMOUS_USER_UUID_HEADER, userUuidValue);
        }

        return request;
    }

    private MockHttpServletRequestBuilder shareRequest(UUID memoId, UUID userUuid) {
        return post("/api/v1/community/memos/{memoUuid}/share", memoId).header(ANONYMOUS_USER_UUID_HEADER,
            userUuid.toString());
    }

    private String publicUrl(String objectKey) {
        return "http://localhost:9000/nemonic-local/" + objectKey.replace(" ", "%20");
    }

    private UUID createdMemoId(MvcResult result) throws Exception {
        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("memoUuid").asText());
    }

    private String validReportJson(String reason) {
        return """
            {
              "reason": "%s"
            }
            """.formatted(reason);
    }

    private String reportJson(String reason, String reasonDetail) {
        return """
            {
              "reason": "%s",
              "reasonDetail": "%s"
            }
            """.formatted(reason, reasonDetail);
    }

    private String validLayoutJson() {
        return """
            {
              "positionX": 120.5,
              "positionY": -30.0,
              "zIndex": 12,
              "rotationDeg": 5.5
            }
            """;
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
