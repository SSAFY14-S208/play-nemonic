package com.nemonicworld.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
        // H2 통합 테스트에서는 갤러리/커뮤니티 최소 스키마만 직접 구성해 조회 정책을 고정합니다.
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
                decoration VARCHAR(1000) NULL DEFAULT '{}',
                body_image_url VARCHAR(1000) NULL,
                attached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                report_count INT NOT NULL DEFAULT 0,
                is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
                moderation_status VARCHAR(32) NOT NULL DEFAULT 'pending',
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP NULL
            )
            """);
        // 다른 테스트가 만든 community_memo 테이블과도 공존하도록 상세 조회에 필요한 컬럼을 보강합니다.
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_x DOUBLE PRECISION DEFAULT 0 NOT NULL");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS position_y DOUBLE PRECISION DEFAULT 0 NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS z_index INT DEFAULT 0 NOT NULL");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS rotation_deg REAL DEFAULT 0 NOT NULL");
        jdbcTemplate.execute(
            "ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS decoration VARCHAR(1000) NULL " + "DEFAULT '{}'");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS body_image_url VARCHAR(1000) NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS attached_at TIMESTAMP "
            + "DEFAULT CURRENT_TIMESTAMP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS report_count INT DEFAULT 0 NOT NULL");
        jdbcTemplate
            .execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT FALSE NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(32) "
            + "DEFAULT 'pending' NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS created_at TIMESTAMP "
            + "DEFAULT CURRENT_TIMESTAMP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP "
            + "DEFAULT CURRENT_TIMESTAMP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN position_x SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN position_y SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN z_index SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN rotation_deg SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN decoration SET DEFAULT '{}'");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN attached_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN report_count SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN is_hidden SET DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN moderation_status SET DEFAULT 'pending'");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE community_memo ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP");

        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM file_upload");
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

    /**
     * DIRECT 메모 상세가 목록 공통 필드와 상세 필드를 함께 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemoReturnsDirectMemoDetail() throws Exception {
        UUID userUuid = createExistingUser("상세");
        LocalDateTime attachedAt = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime createdAt = attachedAt.minusMinutes(1);
        LocalDateTime updatedAt = attachedAt.plusMinutes(1);
        UUID memoId = UUID.randomUUID();

        insertCommunityMemo(memoId, userUuid, null, DIRECT_OBJECT_KEY, 3, attachedAt, null, false, "{\"scale\":1.0}", 2,
            "allowed", createdAt, updatedAt);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("커뮤니티 메모 상세 조회 성공"))
            .andExpect(jsonPath("$.data.memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.authorNickname").value("상세"))
            .andExpect(jsonPath("$.data.sourceType").value("DIRECT"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(DIRECT_PUBLIC_URL))
            .andExpect(jsonPath("$.data.positionX").value(120.5)).andExpect(jsonPath("$.data.positionY").value(80.0))
            .andExpect(jsonPath("$.data.zIndex").value(3)).andExpect(jsonPath("$.data.rotationDeg").value(-4.5))
            .andExpect(jsonPath("$.data.ownedByMe").value(false)).andExpect(jsonPath("$.data.attachedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.artifactId").value(nullValue()))
            .andExpect(jsonPath("$.data.galleryContentKind").value(nullValue()))
            .andExpect(jsonPath("$.data.moderationStatus").value("allowed"))
            .andExpect(jsonPath("$.data.reportCount").value(2)).andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty()).andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.authorUuid").doesNotExist());
    }

    /**
     * GALLERY 메모 상세가 artifact 식별자, kind, 대표 이미지 URL과 ownedByMe=true를 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemoReturnsGalleryMemoDetailWithOwnedByMe() throws Exception {
        UUID userUuid = createExistingUser("릴레이");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();

        insertArtifact(artifactId, "relay_drawing", OBJECT_KEY_PREFIX + "relay-thumb.png", now);
        insertSubtypeArtifact("relay_drawing", artifactId, OBJECT_KEY_PREFIX + "relay.png");
        insertCommunityMemo(memoId, userUuid, artifactId, null, 4, now, null, false, "{\"frame\":\"gold\"}", 1,
            "pending", now.minusMinutes(1), now.plusMinutes(1));

        mockMvc
            .perform(
                get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(PUBLIC_URL_PREFIX + "relay.png"))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.decoration.frame").value("gold"))
            .andExpect(jsonPath("$.data.moderationStatus").value("pending"))
            .andExpect(jsonPath("$.data.reportCount").value(1));
    }

    /**
     * 상세 조회도 optional 헤더 기준으로 ownedByMe를 계산하고, 헤더가 없거나 다르면 false를 반환합니다.
     */
    @Test
    void getCommunityMemoCalculatesOwnedByMeFromOptionalViewerUuid() throws Exception {
        UUID userUuid = createExistingUser("소유자");
        UUID memoId = insertDirectMemo(userUuid, "owned.png", 1, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            null, false);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownedByMe").value(false));

        mockMvc
            .perform(
                get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownedByMe").value(true));

        mockMvc
            .perform(get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER,
                UUID.randomUUID().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownedByMe").value(false));
    }

    /**
     * decoration이 null, blank, 깨진 JSON이어도 서버 오류 대신 빈 객체로 fallback하는지 검증합니다.
     */
    @Test
    void getCommunityMemoFallsBackToEmptyDecorationForNullBlankAndInvalidJson() throws Exception {
        UUID userUuid = createExistingUser("꾸미기");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID nullDecorationMemoId = UUID.randomUUID();
        UUID blankDecorationMemoId = UUID.randomUUID();
        UUID invalidDecorationMemoId = UUID.randomUUID();

        insertCommunityMemo(nullDecorationMemoId, userUuid, null, "null-decoration.png", 1, now, null, false, null, 0,
            "pending", now, now);
        insertCommunityMemo(blankDecorationMemoId, userUuid, null, "blank-decoration.png", 2, now.plusMinutes(1), null,
            false, " ", 0, "pending", now, now);
        insertCommunityMemo(invalidDecorationMemoId, userUuid, null, "invalid-decoration.png", 3, now.plusMinutes(2),
            null, false, "{broken", 0, "pending", now, now);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", nullDecorationMemoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decoration").value(anEmptyMap()));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", blankDecorationMemoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decoration").value(anEmptyMap()));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", invalidDecorationMemoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decoration").value(anEmptyMap()));
    }

    /**
     * 없는 메모, 삭제된 메모, 숨김 메모는 모두 같은 404 응답으로 처리하는지 검증합니다.
     */
    @Test
    void getCommunityMemoReturnsNotFoundForMissingDeletedAndHiddenMemos() throws Exception {
        UUID userUuid = createExistingUser("조회불가");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID deletedMemoId = insertDirectMemo(userUuid, "deleted.png", 1, now, now, false);
        UUID hiddenMemoId = insertDirectMemo(userUuid, "hidden.png", 2, now.plusMinutes(1), null, true);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", UUID.randomUUID())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", deletedMemoId)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
        mockMvc.perform(get("/api/v1/community/memos/{memoId}", hiddenMemoId)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("존재하지 않는 커뮤니티 메모입니다."));
    }

    /**
     * memoId 또는 optional 헤더 UUID 형식이 잘못되면 기존 invalid UUID 400 응답을 반환하는지 검증합니다.
     */
    @Test
    void getCommunityMemoRejectsInvalidUuidValues() throws Exception {
        UUID userUuid = createExistingUser("검증");
        UUID memoId = insertDirectMemo(userUuid, "memo.png", 1, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            null, false);

        mockMvc.perform(get("/api/v1/community/memos/not-a-uuid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    /**
     * 비정상 로컬 데이터에서 작성자나 artifact가 없어도 상세 조회가 서버 오류로 터지지 않는지 검증합니다.
     */
    @Test
    void getCommunityMemoReturnsNullFieldsForOrphanRowsWithoutServerError() throws Exception {
        UUID missingAuthorUuid = UUID.randomUUID();
        UUID missingArtifactId = UUID.randomUUID();
        UUID memoId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        insertCommunityMemo(memoId, missingAuthorUuid, missingArtifactId, null, 1, now, null, false, "{}", 0, "pending",
            now, now);

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.authorNickname").value(nullValue()))
            .andExpect(jsonPath("$.data.sourceType").value("GALLERY"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.artifactId").value(missingArtifactId.toString()))
            .andExpect(jsonPath("$.data.galleryContentKind").value(nullValue()));
    }

    /**
     * 상세 조회 성공은 사용자의 방문 메타데이터를 갱신하지 않는지 검증합니다.
     */
    @Test
    void getCommunityMemoDoesNotUpdateUserMetadata() throws Exception {
        UUID userUuid = createExistingUser("상세방문");
        AppUser beforeUser = userRepository.findById(userUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();
        UUID memoId = insertDirectMemo(userUuid, "memo.png", 1, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            null, false);

        mockMvc
            .perform(
                get("/api/v1/community/memos/{memoId}", memoId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk());

        AppUser afterUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
    }

    @Test
    void createCommunityMemoCreatesDirectMemoFromConfirmedCommunityFile() throws Exception {
        UUID userUuid = createExistingUser("생성메모");
        UUID fileId = insertFileUpload(userUuid, DIRECT_OBJECT_KEY, "COMMUNITY", "UPLOADED", null);

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "fileId": "%s",
                      "positionX": 12.5,
                      "positionY": -7.25,
                      "zIndex": 10,
                      "rotationDeg": 5.5,
                      "decoration": {
                        "scale": 1.0,
                        "theme": "default"
                      }
                    }
                    """.formatted(fileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 생성 성공"))
            .andExpect(jsonPath("$.data.authorNickname").value("생성메모"))
            .andExpect(jsonPath("$.data.sourceType").value("DIRECT"))
            .andExpect(jsonPath("$.data.memoImageUrl").value(DIRECT_PUBLIC_URL))
            .andExpect(jsonPath("$.data.positionX").value(12.5)).andExpect(jsonPath("$.data.positionY").value(-7.25))
            .andExpect(jsonPath("$.data.zIndex").value(10)).andExpect(jsonPath("$.data.rotationDeg").value(5.5))
            .andExpect(jsonPath("$.data.ownedByMe").value(true))
            .andExpect(jsonPath("$.data.decoration.scale").value(1.0))
            .andExpect(jsonPath("$.data.decoration.theme").value("default"))
            .andExpect(jsonPath("$.data.artifactId").value(nullValue()))
            .andExpect(jsonPath("$.data.galleryContentKind").value(nullValue()))
            .andExpect(jsonPath("$.data.moderationStatus").value("pending"))
            .andExpect(jsonPath("$.data.reportCount").value(0)).andExpect(jsonPath("$.data.attachedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty()).andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.authorUuid").doesNotExist());

        UUID memoId = jdbcTemplate.queryForObject("SELECT id FROM community_memo WHERE body_image_url = ?", UUID.class,
            DIRECT_OBJECT_KEY);
        assertThat(memoId).isNotNull();
        assertThat(
            jdbcTemplate.queryForObject("SELECT body_image_url FROM community_memo WHERE id = ?", String.class, memoId))
            .isEqualTo(DIRECT_OBJECT_KEY);
        assertThat(
            jdbcTemplate.queryForObject("SELECT artifact_id FROM community_memo WHERE id = ?", UUID.class, memoId))
            .isNull();
        assertThat(
            jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class, memoId))
            .isZero();
        assertThat(
            jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class, memoId))
            .isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT moderation_status FROM community_memo WHERE id = ?",
            String.class, memoId)).isEqualTo("pending");

        mockMvc.perform(get("/api/v1/community/memos/{memoId}", memoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memoUuid").value(memoId.toString()))
            .andExpect(jsonPath("$.data.memoImageUrl").value(DIRECT_PUBLIC_URL));
        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].memoUuid").value(memoId.toString()));
    }

    @Test
    void createCommunityMemoStoresEmptyDecorationWhenDecorationIsOmitted() throws Exception {
        UUID userUuid = createExistingUser("기본데코");
        UUID fileId = insertFileUpload(userUuid, "uploads/community/2026/05/07/direct-user/no-decoration.png",
            "COMMUNITY", "UPLOADED", null);

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
                    """.formatted(fileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.decoration").value(anEmptyMap()));

        assertThat(jdbcTemplate.queryForObject("SELECT decoration FROM community_memo WHERE body_image_url = ?",
            String.class, "uploads/community/2026/05/07/direct-user/no-decoration.png")).isEqualTo("{}");
    }

    @Test
    void createCommunityMemoValidatesFileUploadOwnershipPurposeAndStatus() throws Exception {
        UUID userUuid = createExistingUser("파일검증");
        UUID otherUserUuid = createExistingUser("다른소유");
        UUID otherUserFileId = insertFileUpload(otherUserUuid, "uploads/community/other-owner.png", "COMMUNITY",
            "UPLOADED", null);
        UUID wrongPurposeFileId = insertFileUpload(userUuid, "uploads/community/wrong-purpose.png", "RELAY_DRAWING",
            "UPLOADED", null);
        UUID pendingFileId = insertFileUpload(userUuid, "uploads/community/pending.png", "COMMUNITY", "PENDING", null);
        UUID deletedStatusFileId = insertFileUpload(userUuid, "uploads/community/deleted-status.png", "COMMUNITY",
            "DELETED", null);
        UUID softDeletedFileId = insertFileUpload(userUuid, "uploads/community/soft-deleted.png", "COMMUNITY",
            "UPLOADED", LocalDateTime.now());

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(UUID.randomUUID().toString())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(otherUserFileId.toString())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(wrongPurposeFileId.toString())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 원본 정보가 올바르지 않습니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(pendingFileId.toString())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(deletedStatusFileId.toString())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(softDeletedFileId.toString())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
    }

    @Test
    void createCommunityMemoValidatesRequiredHeaderSourceAndPayload() throws Exception {
        UUID userUuid = createExistingUser("요청검증");
        UUID fileId = insertFileUpload(userUuid, "uploads/community/request-validation.png", "COMMUNITY", "UPLOADED",
            null);

        mockMvc
            .perform(post("/api/v1/community/memos").contentType(MediaType.APPLICATION_JSON)
                .content(directCreateRequest(fileId.toString())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(fileId.toString())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(fileId.toString())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequestWithSource(fileId, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지원하지 않는 커뮤니티 메모 sourceType입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequestWithSource(fileId, "GALLERY")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지원하지 않는 커뮤니티 메모 sourceType입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequestWithSource(fileId, "UNKNOWN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지원하지 않는 커뮤니티 메모 sourceType입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "positionX": 0.0,
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 fileId 형식입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "fileId": "%s",
                      "galleryId": "8d25f3a5-3c5a-4f21-9f54-68fa4a402011",
                      "positionX": 0.0,
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """.formatted(fileId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 원본 정보가 올바르지 않습니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest("not-a-uuid")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 fileId 형식입니다."));
        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "sourceType": "DIRECT",
                      "fileId": "%s",
                      "positionY": 0.0,
                      "zIndex": 1,
                      "rotationDeg": 0.0
                    }
                    """.formatted(fileId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 위치 정보가 올바르지 않습니다."));
    }

    @Test
    void createCommunityMemoRejectsNonObjectDecoration() throws Exception {
        UUID userUuid = createExistingUser("데코검증");
        UUID fileId = insertFileUpload(userUuid, "uploads/community/decoration-validation.png", "COMMUNITY", "UPLOADED",
            null);

        mockMvc.perform(createRequestWithDecoration(userUuid, fileId, "[1,2,3]")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."));
        mockMvc.perform(createRequestWithDecoration(userUuid, fileId, "\"memo\"")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."));
        mockMvc.perform(createRequestWithDecoration(userUuid, fileId, "1")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."));
        mockMvc.perform(createRequestWithDecoration(userUuid, fileId, "true")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("커뮤니티 메모 데코레이션 정보가 올바르지 않습니다."));
    }

    @Test
    void createCommunityMemoDoesNotApplyFifoLimitYet() throws Exception {
        UUID userUuid = createExistingUser("FIFO제외");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 50; i++) {
            insertDirectMemo(userUuid, "uploads/community/existing-" + i + ".png", i, baseTime.plusMinutes(i), null,
                false);
        }
        UUID fileId = insertFileUpload(userUuid, "uploads/community/2026/05/07/direct-user/fifo-excluded.png",
            "COMMUNITY", "UPLOADED", null);

        mockMvc
            .perform(post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(directCreateRequest(fileId.toString())))
            .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_memo WHERE deleted_at IS NULL AND is_hidden = FALSE", Integer.class))
            .isEqualTo(51);
        mockMvc.perform(get("/api/v1/community/memos")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(51));
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

    private String directCreateRequest(String fileIdValue) {
        return """
            {
              "sourceType": "DIRECT",
              "fileId": "%s",
              "positionX": 0.0,
              "positionY": 0.0,
              "zIndex": 1,
              "rotationDeg": 0.0
            }
            """.formatted(fileIdValue);
    }

    private String directCreateRequestWithSource(UUID fileId, String sourceType) {
        String sourceTypeLine = sourceType == null ? "" : "\"sourceType\": \"%s\",".formatted(sourceType);
        return """
            {
              %s
              "fileId": "%s",
              "positionX": 0.0,
              "positionY": 0.0,
              "zIndex": 1,
              "rotationDeg": 0.0
            }
            """.formatted(sourceTypeLine, fileId);
    }

    private MockHttpServletRequestBuilder createRequestWithDecoration(UUID userUuid, UUID fileId, String decoration) {
        return post("/api/v1/community/memos").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "sourceType": "DIRECT",
                  "fileId": "%s",
                  "positionX": 0.0,
                  "positionY": 0.0,
                  "zIndex": 1,
                  "rotationDeg": 0.0,
                  "decoration": %s
                }
                """.formatted(fileId, decoration));
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
        insertCommunityMemo(memoId, userUuid, artifactId, bodyImageUrl, zIndex, attachedAt, deletedAt, hidden, "{}", 0,
            "pending", attachedAt, attachedAt);
    }

    private void insertCommunityMemo(UUID memoId, UUID userUuid, UUID artifactId, String bodyImageUrl, int zIndex,
        LocalDateTime attachedAt, LocalDateTime deletedAt, boolean hidden, String decoration, int reportCount,
        String moderationStatus, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
            INSERT INTO community_memo (
                id, user_id, artifact_id, position_x, position_y, z_index, rotation_deg, body_image_url,
                attached_at, is_hidden, deleted_at, decoration, report_count, moderation_status, created_at, updated_at
            )
            VALUES (?, ?, ?, 120.5, 80.0, ?, -4.5, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, memoId, userUuid, artifactId, zIndex, bodyImageUrl, attachedAt, hidden, deletedAt, decoration,
            reportCount, moderationStatus, createdAt, updatedAt);
    }
}
