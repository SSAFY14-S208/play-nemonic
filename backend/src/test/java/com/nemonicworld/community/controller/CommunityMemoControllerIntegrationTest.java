package com.nemonicworld.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 커뮤니티 공용 벽 메모 목록 조회 API의 조회, 필터링, 정렬, 소유권 계산을 통합 검증합니다.
 */
class CommunityMemoControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DIRECT_OBJECT_KEY = "uploads/community/2026/05/07/direct-user/memo image.png";
    private static final String DIRECT_PUBLIC_URL = "http://localhost:9000/nemonic-local/uploads/community/2026/05/07/"
        + "direct-user/memo%20image.png";
    private static final String OBJECT_KEY_PREFIX = "uploads/community/2026/05/07/gallery/";
    private static final String PUBLIC_URL_PREFIX = "http://localhost:9000/nemonic-local/" + OBJECT_KEY_PREFIX;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void prepareCommunityTables() {
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
                fortune_image_url VARCHAR(200) NULL
            )
            """);
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
                body_image_url VARCHAR(1000) NULL,
                attached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_x DOUBLE PRECISION DEFAULT 0 NOT NULL");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_y DOUBLE PRECISION DEFAULT 0 NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS z_index INT DEFAULT 0 NOT NULL");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS rotation_deg REAL DEFAULT 0 NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS body_image_url VARCHAR(1000) NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS attached_at TIMESTAMP "
            + "DEFAULT CURRENT_TIMESTAMP NOT NULL");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT FALSE NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN position_x SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN position_y SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN z_index SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN rotation_deg SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN attached_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN is_hidden SET DEFAULT FALSE");

        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM flipbook_artifact");
        jdbcTemplate.update("DELETE FROM infinite_canvas_artifact");
        jdbcTemplate.update("DELETE FROM phone_artifact");
        jdbcTemplate.update("DELETE FROM artifact");
    }

    /**
     * visible 조건만 조회하고 z_index, attached_at 순서로 정렬하며 DIRECT 이미지를 public URL로 변환하는지
     * 검증합니다.
     */
    @Test
    void getCommunityMemosReturnsVisibleDirectMemosOrderedByZIndexAndAttachedAt() throws Exception {
        UUID userUuid = createExistingUser("망고");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID firstMemoId = insertDirectMemo(userUuid, "first.png", 2, baseTime.plusMinutes(2), null, false);
        UUID secondMemoId = insertDirectMemo(userUuid, DIRECT_OBJECT_KEY, 1, baseTime.plusMinutes(2), null, false);
        UUID thirdMemoId = insertDirectMemo(userUuid, "third.png", 1, baseTime.plusMinutes(1), null, false);

        insertDirectMemo(userUuid, "deleted.png", 0, baseTime, baseTime, false);
        insertDirectMemo(userUuid, "hidden.png", 0, baseTime, null, true);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("커뮤니티 메모 목록 조회 성공"))
            .andExpect(jsonPath("$.data.items", hasSize(3))).andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(thirdMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].memoUuid").value(secondMemoId.toString()))
            .andExpect(jsonPath("$.data.items[2].memoUuid").value(firstMemoId.toString()))
            .andExpect(jsonPath("$.data.items[1].authorNickname").value("망고"))
            .andExpect(jsonPath("$.data.items[1].sourceType").value("DIRECT"))
            .andExpect(jsonPath("$.data.items[1].memoImageUrl").value(DIRECT_PUBLIC_URL))
            .andExpect(jsonPath("$.data.items[1].positionX").value(120.5))
            .andExpect(jsonPath("$.data.items[1].positionY").value(80.0))
            .andExpect(jsonPath("$.data.items[1].zIndex").value(1))
            .andExpect(jsonPath("$.data.items[1].rotationDeg").value(-4.5))
            .andExpect(jsonPath("$.data.items[1].ownedByMe").value(false))
            .andExpect(jsonPath("$.data.items[1].attachedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.items[1].userId").doesNotExist())
            .andExpect(jsonPath("$.data.items[1].authorUuid").doesNotExist());
    }

    /**
     * GALLERY 메모의 대표 이미지가 artifact kind별 subtype URL로 선택되고, 없으면 thumbnail로
     * fallback되는지 검증합니다.
     */
    @Test
    void getCommunityMemosMapsGalleryImageReferenceByArtifactKindAndFallback() throws Exception {
        UUID userUuid = createExistingUser("갤러리");
        LocalDateTime baseTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        insertGalleryMemo(userUuid, "fortune", "fortune-thumb.png", "fortune.png", 1, baseTime.plusMinutes(1));
        insertGalleryMemo(userUuid, "relay_drawing", "relay-thumb.png", "relay.png", 2, baseTime.plusMinutes(2));
        insertGalleryMemo(userUuid, "flipbook", "flipbook-thumb.png", "https://cdn.example.com/flipbook.gif", 3,
            baseTime.plusMinutes(3));
        insertGalleryMemo(userUuid, "infinite_canvas", "canvas-thumb.png", "canvas.png", 4, baseTime.plusMinutes(4));
        insertGalleryMemo(userUuid, "phone", "phone-thumb.png", "phone.png", 5, baseTime.plusMinutes(5));
        insertGalleryMemo(userUuid, "community_memo", "community-thumb.png", null, 6, baseTime.plusMinutes(6));
        insertGalleryMemo(userUuid, "fortune", "fallback-thumb.png", null, 7, baseTime.plusMinutes(7));
        insertGalleryMemo(userUuid, "fortune", "blank-fallback-thumb.png", "", 8, baseTime.plusMinutes(8));

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(8)))
            .andExpect(jsonPath("$.data.items[0].sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.items[0].memoImageUrl").value(PUBLIC_URL_PREFIX + "fortune.png"))
            .andExpect(jsonPath("$.data.items[1].memoImageUrl").value(PUBLIC_URL_PREFIX + "relay.png"))
            .andExpect(jsonPath("$.data.items[2].memoImageUrl").value("https://cdn.example.com/flipbook.gif"))
            .andExpect(jsonPath("$.data.items[3].memoImageUrl").value(PUBLIC_URL_PREFIX + "canvas.png"))
            .andExpect(jsonPath("$.data.items[4].memoImageUrl").value(PUBLIC_URL_PREFIX + "phone.png"))
            .andExpect(jsonPath("$.data.items[5].memoImageUrl").value(PUBLIC_URL_PREFIX + "community-thumb.png"))
            .andExpect(jsonPath("$.data.items[6].memoImageUrl").value(PUBLIC_URL_PREFIX + "fallback-thumb.png"))
            .andExpect(jsonPath("$.data.items[7].memoImageUrl").value(PUBLIC_URL_PREFIX + "blank-fallback-thumb.png"));
    }

    /**
     * optional Anonymous-User-UUID가 작성자와 같을 때만 ownedByMe=true로 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemosCalculatesOwnedByMeFromOptionalViewerUuid() throws Exception {
        UUID ownerUuid = createExistingUser("주인");
        UUID otherAuthorUuid = createExistingUser("타인");
        UUID viewerUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        insertDirectMemo(ownerUuid, "owner.png", 1, now, null, false);
        insertDirectMemo(otherAuthorUuid, "other.png", 2, now.plusMinutes(1), null, false);

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].ownedByMe").value(true))
            .andExpect(jsonPath("$.data.items[1].ownedByMe").value(false));

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].ownedByMe").value(false))
            .andExpect(jsonPath("$.data.items[1].ownedByMe").value(false));
    }

    /**
     * 헤더 UUID 형식이 잘못되면 기존 invalid UUID 400 응답을 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemosRejectsInvalidViewerUuid() throws Exception {
        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    /**
     * 목록 조회 성공은 사용자의 방문 메타데이터를 갱신하지 않는지 검증합니다.
     */
    @Test
    void getCommunityMemosDoesNotUpdateUserMetadata() throws Exception {
        UUID userUuid = createExistingUser("방문자");
        AppUser beforeUser = userRepository.findById(userUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        insertDirectMemo(userUuid, "memo.png", 1, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null, false);

        mockMvc.perform(get("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk());

        AppUser afterUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
    }

    /**
     * 비정상 로컬 데이터에서 작성자나 artifact가 없어도 서버 오류 대신 null 필드를 포함한 목록으로 방어 응답합니다.
     */
    @Test
    void getCommunityMemosReturnsNullFieldsForOrphanRowsWithoutServerError() throws Exception {
        UUID missingAuthorUuid = UUID.randomUUID();
        UUID missingArtifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        insertCommunityMemo(memoId, missingAuthorUuid, missingArtifactId, null, 1, now, null, false);

        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.items[0].authorNickname").value(nullValue()))
            .andExpect(jsonPath("$.data.items[0].sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.items[0].memoImageUrl").value(nullValue()));
    }

    /**
     * 메모가 없으면 빈 목록과 totalElements=0을 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemosReturnsEmptyItemsWhenNoVisibleMemosExist() throws Exception {
        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(0))).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private UUID createExistingUser(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, createdAt);
        userRepository.saveAndFlush(appUser);

        return userUuid;
    }

    private UUID insertDirectMemo(UUID userUuid, String bodyImageUrl, int zIndex, LocalDateTime attachedAt,
        LocalDateTime deletedAt, boolean hidden) {
        UUID memoId = UUID.randomUUID();
        insertCommunityMemo(memoId, userUuid, null, bodyImageUrl, zIndex, attachedAt, deletedAt, hidden);

        return memoId;
    }

    private void insertGalleryMemo(UUID userUuid, String kind, String thumbnailFileName, String contentFileName,
        int zIndex, LocalDateTime attachedAt) {
        UUID artifactId = UUID.randomUUID();
        String thumbnailUrl = OBJECT_KEY_PREFIX + thumbnailFileName;
        String contentUrl = contentFileName == null || contentFileName.isBlank() || contentFileName.startsWith("http")
            ? contentFileName
            : OBJECT_KEY_PREFIX + contentFileName;

        insertArtifact(artifactId, kind, thumbnailUrl, attachedAt);
        insertSubtypeArtifact(kind, artifactId, contentUrl);
        insertCommunityMemo(UUID.randomUUID(), userUuid, artifactId, null, zIndex, attachedAt, null, false);
    }

    private void insertArtifact(UUID artifactId, String kind, String thumbnailUrl, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, NULL, ?, '{}', ?, ?)
            """, artifactId, kind, thumbnailUrl, createdAt, createdAt);
    }

    private void insertSubtypeArtifact(String kind, UUID artifactId, String contentUrl) {
        if ("fortune".equals(kind)) {
            jdbcTemplate.update(
                "INSERT INTO fortune_artifact (artifact_id, description, fortune_image_url) VALUES (?, '{}', ?)",
                artifactId, contentUrl);
        } else if ("relay_drawing".equals(kind)) {
            jdbcTemplate.update("INSERT INTO relay_drawing_artifact (artifact_id, combined_preview_url) VALUES (?, ?)",
                artifactId, contentUrl);
        } else if ("flipbook".equals(kind)) {
            jdbcTemplate.update("INSERT INTO flipbook_artifact (artifact_id, gif_url, first_image) VALUES (?, ?, ?)",
                artifactId, contentUrl, "first-image");
        } else if ("infinite_canvas".equals(kind)) {
            jdbcTemplate.update("INSERT INTO infinite_canvas_artifact (artifact_id, canvas_image_url) VALUES (?, ?)",
                artifactId, contentUrl);
        } else if ("phone".equals(kind)) {
            jdbcTemplate.update("INSERT INTO phone_artifact (artifact_id, phone_image_url) VALUES (?, ?)", artifactId,
                contentUrl);
        }
    }

    private void insertCommunityMemo(UUID memoId, UUID userUuid, UUID artifactId, String bodyImageUrl, int zIndex,
        LocalDateTime attachedAt, LocalDateTime deletedAt, boolean hidden) {
        jdbcTemplate.update("""
            INSERT INTO community_memo (
                id, user_id, artifact_id, position_x, position_y, z_index, rotation_deg, body_image_url,
                attached_at, is_hidden, deleted_at
            )
            VALUES (?, ?, ?, 120.5, 80.0, ?, -4.5, ?, ?, ?, ?)
            """, memoId, userUuid, artifactId, zIndex, bodyImageUrl, attachedAt, hidden, deletedAt);
    }
}
