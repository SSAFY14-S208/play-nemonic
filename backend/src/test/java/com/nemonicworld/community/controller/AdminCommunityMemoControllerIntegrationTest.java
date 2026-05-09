package com.nemonicworld.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.auth.service.IssuedAdminRefreshToken;
import com.nemonicworld.auth.service.StoredAdminRefreshToken;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class AdminCommunityMemoControllerIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_LOGIN_ID = "community-admin";
    private static final String ADMIN_EMAIL = "community-admin@example.com";
    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ORIGINAL_OBJECT_KEY = "uploads/community/admin/original.png";
    private static final String THUMBNAIL_OBJECT_KEY = "uploads/community/admin/thumbnail.png";
    private static final String ORIGINAL_PUBLIC_URL = "http://localhost:9000/nemonic-local/" + ORIGINAL_OBJECT_KEY;
    private static final String THUMBNAIL_PUBLIC_URL = "http://localhost:9000/nemonic-local/" + THUMBNAIL_OBJECT_KEY;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CommunityMemoModerationClient moderationClient;

    @BeforeEach
    void prepareTables() {
        createTables();
        cleanTables();
        insertAdminUser();
    }

    @Test
    void adminGetsCommunityMemoListWithHiddenAndOperationFilters() throws Exception {
        UUID authorUuid = insertAppUser("망고");
        UUID artifactId = UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID visibleMemoId = insertCommunityMemo(authorUuid, null, "visible-original.png", null, false, null, null, 1,
            "allowed", "visible ocr", null, baseTime, baseTime.plusMinutes(1));
        UUID hiddenMemoId = insertCommunityMemo(authorUuid, artifactId, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", baseTime.plusMinutes(4), 5, "allowed", "운영 검토 키워드", 9L, baseTime,
            baseTime.plusMinutes(3));
        insertArtifact(artifactId, "relay_drawing", baseTime);
        insertCommunityMemo(authorUuid, null, "deleted-original.png", "deleted-thumbnail.png", false, null, null, 0,
            "allowed", "deleted", null, baseTime, baseTime.plusMinutes(5), baseTime.plusMinutes(6));

        mockMvc.perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("관리자 커뮤니티 메모 목록 조회 성공"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].memoId").value(hiddenMemoId.toString()))
            .andExpect(jsonPath("$.data.items[0].authorUserUuid").value(authorUuid.toString()))
            .andExpect(jsonPath("$.data.items[0].authorNickname").value("망고"))
            .andExpect(jsonPath("$.data.items[0].sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.items[0].artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.items[0].artifactKind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.items[0].memoOriginalImageUrl").value(ORIGINAL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[0].memoThumbnailImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[0].memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[0].isHidden").value(true))
            .andExpect(jsonPath("$.data.items[0].hiddenReason").value("report_threshold"))
            .andExpect(jsonPath("$.data.items[0].reportCount").value(5))
            .andExpect(jsonPath("$.data.items[0].reviewedBy").value(9L))
            .andExpect(jsonPath("$.data.items[1].memoId").value(visibleMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].memoThumbnailImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.items[1].memoImageUrl")
                .value("http://localhost:9000/nemonic-local/visible-original.png"))
            .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("hidden", "true").param("sourceType", "GALLERY").param("moderationStatus", "allowed")
                .param("keyword", "검토"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(hiddenMemoId.toString()));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("hidden", "false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(visibleMemoId.toString()));

        mockMvc.perform(get("/api/v1/admin/community/memos")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminGetsHiddenMemoDetailAndRejectsDeletedOrInvalidMemoId() throws Exception {
        UUID authorUuid = insertAppUser("상세");
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), 5, "allowed", "ocr", 1L,
            LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
        UUID deletedMemoId = insertCommunityMemo(authorUuid, null, "deleted-original.png", "deleted-thumbnail.png",
            false, null, null, 0, "allowed", null, null,
            LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}", memoId).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("관리자 커뮤니티 메모 상세 조회 성공"))
            .andExpect(jsonPath("$.data.memoId").value(memoId.toString()))
            .andExpect(jsonPath("$.data.authorUserUuid").value(authorUuid.toString()))
            .andExpect(jsonPath("$.data.isHidden").value(true))
            .andExpect(jsonPath("$.data.hiddenReason").value("report_threshold"))
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.memoImageUrl").value(THUMBNAIL_PUBLIC_URL));

        mockMvc
            .perform(
                get("/api/v1/admin/community/memos/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}", deletedMemoId).header(HttpHeaders.AUTHORIZATION,
            bearerAccessToken())).andExpect(status().isNotFound());
    }

    @Test
    void adminHidesVisibleMemoWithAdminHiddenAndUserApisExcludeIt() throws Exception {
        UUID authorUuid = insertAppUser("숨김");
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, false, null,
            null, 2, "allowed", "ocr", null, createdAt, createdAt);

        mockMvc
            .perform(patch("/api/v1/admin/community/memos/{memoId}/hide", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "admin_hidden"
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("커뮤니티 메모 숨김 처리 성공"))
            .andExpect(jsonPath("$.data.isHidden").value(true))
            .andExpect(jsonPath("$.data.hiddenReason").value("admin_hidden"))
            .andExpect(jsonPath("$.data.reviewedBy").value(ADMIN_ID));

        assertThat(
            jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class, memoId))
            .isTrue();
        assertThat(
            jdbcTemplate.queryForObject("SELECT hidden_reason FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo("admin_hidden");
        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM community_memo WHERE id = ?", LocalDateTime.class,
            memoId)).isNotNull();
        assertThat(
            jdbcTemplate.queryForObject("SELECT reviewed_by FROM community_memo WHERE id = ?", Long.class, memoId))
            .isEqualTo(ADMIN_ID);
        assertPreservedMemoSnapshot(memoId);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isNotFound());
        mockMvc.perform(
            patch("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, authorUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(layoutJson()))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER,
            authorUuid.toString())).andExpect(status().isNotFound());
        mockMvc
            .perform(post("/api/v1/community/memos/{memoId}/reports", memoId)
                .header(ANONYMOUS_USER_UUID_HEADER, insertAppUser("신고자").toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"기타\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/admin/community/memos/{memoId}/hide", memoId)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "reason": "report_threshold"
                }
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(moderationClient);
    }

    @Test
    void adminRestoresHiddenMemoWithoutImmediateFifoOrDataMutation() throws Exception {
        UUID authorUuid = insertAppUser("복구");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        for (int index = 0; index < 51; index++) {
            insertCommunityMemo(authorUuid, null, "visible-%s.png".formatted(index), null, false, null, null, 0,
                "allowed", null, null, baseTime.plusSeconds(index), baseTime.plusSeconds(index));
        }
        UUID hiddenMemoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", baseTime.plusHours(1), 5, "allowed", "ocr", 9L, baseTime, baseTime.plusHours(1));

        mockMvc
            .perform(patch("/api/v1/admin/community/memos/{memoId}/restore", hiddenMemoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("커뮤니티 메모 숨김 복구 성공"))
            .andExpect(jsonPath("$.data.isHidden").value(false))
            .andExpect(jsonPath("$.data.hiddenReason").value(nullValue()))
            .andExpect(jsonPath("$.data.reviewedBy").value(ADMIN_ID));

        assertThat(jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class,
            hiddenMemoId)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT hidden_reason FROM community_memo WHERE id = ?", String.class,
            hiddenMemoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM community_memo WHERE id = ?", LocalDateTime.class,
            hiddenMemoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class,
            hiddenMemoId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, hiddenMemoId)).isEqualTo("allowed");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
            """, Integer.class)).isEqualTo(52);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo WHERE deleted_reason = 'expired'",
            Integer.class)).isZero();

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", hiddenMemoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memoUuid").value(hiddenMemoId.toString()));
        verifyNoInteractions(moderationClient);
    }

    private void createTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS admin_user (
                id BIGINT PRIMARY KEY,
                login_id VARCHAR(64) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                nickname VARCHAR(20) NOT NULL,
                email VARCHAR(255) NOT NULL,
                role VARCHAR(32) NOT NULL,
                last_login_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS app_user (
                id UUID NOT NULL PRIMARY KEY,
                nickname VARCHAR(10) NOT NULL,
                last_seen_at TIMESTAMP NOT NULL,
                birthday DATE NULL,
                birthtime TIME NULL,
                is_lunar BOOLEAN NULL,
                user_agent TEXT NOT NULL,
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
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP NULL,
                deleted_reason VARCHAR(32) NULL
            )
            """);
    }

    private void cleanTables() {
        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("DELETE FROM admin_user");
    }

    private void insertAdminUser() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO admin_user (
                id, login_id, password_hash, nickname, email, role, last_login_at, created_at, updated_at, deleted_at
            )
            VALUES (?, ?, 'encoded', 'Community Admin', ?, 'admin', NULL, ?, ?, NULL)
            """, ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_EMAIL, now, now);
    }

    private UUID insertAppUser(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO app_user (id, nickname, last_seen_at, user_agent, created_at, updated_at)
            VALUES (?, ?, ?, 'MangoApp/1.0', ?, ?)
            """, userUuid, nickname, now, now, now);

        return userUuid;
    }

    private void insertArtifact(UUID artifactId, String kind, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, NULL, 'artifact-thumbnail.png', '{}', ?, ?)
            """, artifactId, kind, createdAt, createdAt);
    }

    private UUID insertCommunityMemo(UUID userUuid, UUID artifactId, String bodyImageUrl, String thumbnailImageUrl,
        boolean hidden, String hiddenReason, LocalDateTime hiddenAt, int reportCount, String moderationStatus,
        String ocrText, Long reviewedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return insertCommunityMemo(userUuid, artifactId, bodyImageUrl, thumbnailImageUrl, hidden, hiddenReason,
            hiddenAt, reportCount, moderationStatus, ocrText, reviewedBy, createdAt, updatedAt, null);
    }

    private UUID insertCommunityMemo(UUID userUuid, UUID artifactId, String bodyImageUrl, String thumbnailImageUrl,
        boolean hidden, String hiddenReason, LocalDateTime hiddenAt, int reportCount, String moderationStatus,
        String ocrText, Long reviewedBy, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        UUID memoId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO community_memo (
                id, user_id, artifact_id, position_x, position_y, z_index, rotation_deg, decoration, body_image_url,
                thumbnail_image_url, attached_at, report_count, is_hidden, hidden_reason, hidden_at,
                moderation_status, ocr_text, ocr_categories, moderation_checked_at, reviewed_by, created_at,
                updated_at, deleted_at, deleted_reason
            )
            VALUES (
                ?, ?, ?, 120.5, -30.0, 12, 5.5, '{"scale":1.0}', ?, ?, ?, ?, ?, ?, ?, ?, ?, '["safe"]', ?, ?, ?, ?,
                ?, NULL
            )
            """, memoId, userUuid, artifactId, bodyImageUrl, thumbnailImageUrl, createdAt, reportCount, hidden,
            hiddenReason, hiddenAt, moderationStatus, ocrText, createdAt, reviewedBy, createdAt, updatedAt, deletedAt);

        return memoId;
    }

    private String bearerAccessToken() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AdminUser adminUser = new AdminUser(ADMIN_ID, ADMIN_LOGIN_ID, "encoded", "Community Admin", ADMIN_EMAIL,
            AdminRole.ADMIN, null, now, now, null);

        return "Bearer %s".formatted(jwtTokenProvider.createAccessToken(adminUser).accessToken());
    }

    private void assertPreservedMemoSnapshot(UUID memoId) {
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(ORIGINAL_OBJECT_KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_image_url FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo(THUMBNAIL_OBJECT_KEY);
        assertThat(
            jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE id = ?", String.class, memoId))
            .contains("scale");
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo("allowed");
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isNull();
    }

    private String layoutJson() {
        return """
            {
              "positionX": 120.5,
              "positionY": -30.0,
              "zIndex": 12,
              "rotationDeg": 5.5
            }
            """;
    }

    @TestConfiguration
    static class AdminCommunityTokenStoreTestConfig {

        @Bean
        @Primary
        AdminTokenStore adminTokenStore() {
            return new NoOpAdminTokenStore();
        }
    }

    static class NoOpAdminTokenStore implements AdminTokenStore {

        @Override
        public IssuedAdminRefreshToken issueRefreshToken(AdminUser adminUser) {
            Instant expiresAt = Instant.now().plusSeconds(60);

            return new IssuedAdminRefreshToken("unused", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        }

        @Override
        public Optional<StoredAdminRefreshToken> findRefreshToken(String refreshToken) {
            return Optional.empty();
        }

        @Override
        public void revokeRefreshToken(String refreshToken) {
        }

        @Override
        public void revokeAllRefreshTokens(Long adminId) {
        }

        @Override
        public void blacklistAccessToken(AdminTokenClaims claims) {
        }

        @Override
        public void revokeAccessTokensIssuedBefore(Long adminId, Instant revokedAt) {
        }

        @Override
        public boolean isAccessTokenRevoked(AdminTokenClaims claims) {
            return false;
        }
    }
}
