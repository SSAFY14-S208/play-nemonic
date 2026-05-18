package com.nemonicworld.artifact.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.artifact.service.download.ArtifactDownloadFile;
import com.nemonicworld.artifact.service.download.ArtifactDownloadService;
import com.nemonicworld.artifact.service.share.ArtifactShareService;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * artifact ID 기반 산출물 이미지 URL 조회 API를 통합 검증합니다.
 */
class ArtifactControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String MINIO_PUBLIC_URL = "http://localhost:9000/nemonic-local/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private ArtifactDownloadService artifactDownloadService;

    @MockitoBean
    private ArtifactShareService artifactShareService;

    @BeforeEach
    void prepareArtifactTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id UUID PRIMARY KEY,
                kind VARCHAR(32) NOT NULL,
                source_room_id VARCHAR(64) NULL,
                thumbnail_url VARCHAR(200) NOT NULL,
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
            CREATE TABLE IF NOT EXISTS fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL,
                fortune_image_url VARCHAR(200) NOT NULL,
                user_id UUID NOT NULL,
                fortune_date DATE NOT NULL
            )
            """);
        jdbcTemplate.execute("ALTER TABLE fortune_artifact ADD COLUMN IF NOT EXISTS user_id UUID");
        jdbcTemplate.execute("ALTER TABLE fortune_artifact ADD COLUMN IF NOT EXISTS fortune_date DATE");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS flipbook_artifact (
                artifact_id UUID PRIMARY KEY,
                gif_url VARCHAR(200) NULL,
                first_image VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS infinite_canvas_artifact (
                artifact_id UUID PRIMARY KEY,
                canvas_image_url VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS phone_artifact (
                artifact_id UUID PRIMARY KEY,
                phone_image_url VARCHAR(200) NULL
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
                reviewed_by BIGINT NULL,
                reviewed_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP NULL,
                deleted_reason VARCHAR(32) NULL
            )
            """);

        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM flipbook_artifact");
        jdbcTemplate.update("DELETE FROM infinite_canvas_artifact");
        jdbcTemplate.update("DELETE FROM phone_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");
    }

    /**
     * 릴레이 산출물은 공통 썸네일과 combined_preview 콘텐츠 URL 하나를 반환합니다.
     */
    @Test
    void getArtifactImageUrlsReturnsRelayThumbnailAndCombinedPreview() throws Exception {
        UUID userUuid = createExistingUser();
        UUID artifactId = insertRelayArtifact(userUuid, "relay/results/artifact-1/thumbnail.png",
            "relay/results/artifact-1/original.png", null);

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("산출물 이미지 URL 조회 성공"))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.kind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl("relay/results/artifact-1/thumbnail.png")))
            .andExpect(jsonPath("$.data.contents", hasSize(1)))
            .andExpect(jsonPath("$.data.contents[0].type").value("combined_preview"))
            .andExpect(jsonPath("$.data.contents[0].url").value(publicUrl("relay/results/artifact-1/original.png")));
    }

    /**
     * 플립북 산출물은 GIF와 첫 프레임 이미지를 각각 콘텐츠로 반환합니다.
     */
    @Test
    void getArtifactImageUrlsReturnsFlipbookGifAndFirstImage() throws Exception {
        UUID userUuid = createExistingUser();
        UUID artifactId = insertFlipbookArtifact(userUuid, "flipbook/results/artifact-2/thumbnail.png",
            "flipbook/results/artifact-2/result.gif", "flipbook/results/artifact-2/first.png", null);

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.kind").value("flipbook"))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl("flipbook/results/artifact-2/thumbnail.png")))
            .andExpect(jsonPath("$.data.contents", hasSize(2)))
            .andExpect(jsonPath("$.data.contents[0].type").value("gif"))
            .andExpect(jsonPath("$.data.contents[0].url").value(publicUrl("flipbook/results/artifact-2/result.gif")))
            .andExpect(jsonPath("$.data.contents[1].type").value("first_image"))
            .andExpect(jsonPath("$.data.contents[1].url").value(publicUrl("flipbook/results/artifact-2/first.png")));
    }

    /**
     * 이미 absolute URL로 저장된 object reference는 다시 조립하지 않고 그대로 반환합니다.
     */
    @Test
    void getArtifactImageUrlsKeepsAbsoluteUrls() throws Exception {
        UUID userUuid = createExistingUser();
        UUID artifactId = insertRelayArtifact(userUuid, "https://cdn.example.com/thumb.png",
            "https://cdn.example.com/original.png", null);

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/thumb.png"))
            .andExpect(jsonPath("$.data.contents[0].url").value("https://cdn.example.com/original.png"));
    }

    /**
     * soft delete된 갤러리 항목이나 타인의 산출물은 조회할 수 없는 산출물로 처리합니다.
     */
    @Test
    void getArtifactImageUrlsReturnsNotFoundForUnavailableGalleryRows() throws Exception {
        UUID userUuid = createExistingUser();
        UUID otherUserUuid = createExistingUser();
        LocalDateTime deletedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID otherArtifactId = insertRelayArtifact(otherUserUuid, "other-thumb", "other-content", null);
        UUID deletedArtifactId = insertRelayArtifact(userUuid, "deleted-thumb", "deleted-content", deletedAt);

        assertArtifactImageUrlNotFound(userUuid, UUID.randomUUID());
        assertArtifactImageUrlNotFound(userUuid, otherArtifactId);
        assertArtifactImageUrlNotFound(userUuid, deletedArtifactId);
    }

    /**
     * 헤더 UUID나 artifactId 형식이 잘못되면 각각의 400 메시지를 반환합니다.
     */
    @Test
    void getArtifactImageUrlsRejectsInvalidUuidValues() throws Exception {
        mockMvc.perform(get("/api/v1/artifacts/{artifactId}/image-urls", UUID.randomUUID()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", UUID.randomUUID())
                .header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", "not-an-artifact-id")
                .header(ANONYMOUS_USER_UUID_HEADER, UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 산출물 ID 형식입니다."));
    }

    /**
     * 존재하지 않는 사용자 UUID로 조회하면 새 사용자를 만들지 않고 404를 반환합니다.
     */
    @Test
    void getArtifactImageUrlsReturnsNotFoundForMissingUserAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", UUID.randomUUID())
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * 다운로드 API는 QR 합성본 파일 바이트와 attachment 파일명을 그대로 내려줍니다.
     */
    @Test
    void downloadArtifactReturnsQrComposedFile() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        byte[] fileBytes = new byte[]{1, 2, 3, 4};

        when(artifactDownloadService.prepareDownloadFile(userUuid.toString(), artifactId.toString()))
            .thenReturn(new ArtifactDownloadFile(fileBytes, "nemonic-result.jpg", "image/jpeg"));

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/download", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("nemonic-result.jpg")))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(fileBytes));
    }

    /**
     * artifact 공유 API는 QR 합성 이미지 URL과 플랫폼별 공유 URL을 JSON으로 반환합니다.
     */
    @Test
    void createArtifactShareReturnsQrImageUrlAndShareUrls() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ShareCreateResponse response = new ShareCreateResponse("signed-share-token",
            "https://minio.example.com/nemonic/artifact-downloads/result-qr-v2.jpg", "https://nemonic.example.com",
            "https://nemonic.example.com?utm_source=kakao", "https://nemonic.example.com?utm_source=instagram");

        when(artifactShareService.createArtifactShare(userUuid.toString(), artifactId.toString())).thenReturn(response);

        mockMvc
            .perform(post("/api/v1/artifacts/{artifactId}/share", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("산출물 공유 정보 생성 성공"))
            .andExpect(jsonPath("$.data.shareToken").value("signed-share-token"))
            .andExpect(jsonPath("$.data.imageUrl")
                .value("https://minio.example.com/nemonic/artifact-downloads/result-qr-v2.jpg"))
            .andExpect(jsonPath("$.data.siteUrl").value("https://nemonic.example.com"))
            .andExpect(jsonPath("$.data.kakaoUrl").value("https://nemonic.example.com?utm_source=kakao"))
            .andExpect(jsonPath("$.data.instagramUrl").value("https://nemonic.example.com?utm_source=instagram"));
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private UUID insertRelayArtifact(UUID userUuid, String thumbnailUrl, String contentUrl, LocalDateTime deletedAt) {
        UUID artifactId = insertArtifact("relay_drawing", thumbnailUrl);
        jdbcTemplate.update("INSERT INTO relay_drawing_artifact (artifact_id, combined_preview_url) VALUES (?, ?)",
            artifactId, contentUrl);
        insertGallery(userUuid, artifactId, deletedAt);

        return artifactId;
    }

    private UUID insertFlipbookArtifact(UUID userUuid, String thumbnailUrl, String gifUrl, String firstImageUrl,
        LocalDateTime deletedAt) {
        UUID artifactId = insertArtifact("flipbook", thumbnailUrl);
        jdbcTemplate.update("INSERT INTO flipbook_artifact (artifact_id, gif_url, first_image) VALUES (?, ?, ?)",
            artifactId, gifUrl, firstImageUrl);
        insertGallery(userUuid, artifactId, deletedAt);

        return artifactId;
    }

    private UUID insertArtifact(String kind, String thumbnailUrl) {
        UUID artifactId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, ?, ?, '{}', ?, ?)
            """, artifactId, kind, "ROOM-1", thumbnailUrl, now, now);

        return artifactId;
    }

    private void insertGallery(UUID userUuid, UUID artifactId, LocalDateTime deletedAt) {
        jdbcTemplate.update("INSERT INTO gallery (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userUuid, artifactId, deletedAt);
    }

    private void assertArtifactImageUrlNotFound(UUID userUuid, UUID artifactId) throws Exception {
        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("조회 가능한 산출물 이미지 URL을 찾을 수 없습니다."));
    }

    private String publicUrl(String objectKey) {
        return MINIO_PUBLIC_URL + objectKey;
    }
}
