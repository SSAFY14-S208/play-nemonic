package com.nemonicworld.gallery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.sql.Timestamp;
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
 * 내 갤러리 목록 조회 API의 정상, 예외, 페이지 흐름을 통합 검증합니다.
 */
class GalleryControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String MINIO_PUBLIC_URL = "http://localhost:9000/nemonic-local/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void prepareGalleryTables() {
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
                deleted_at TIMESTAMP NULL
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
     * 여러 종류의 결과물이 artifact.created_at 기준 최신순으로 반환되는지 검증합니다.
     */
    @Test
    void getMyGalleryReturnsItemsOrderedByArtifactCreatedAtDesc() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        insertGalleryItem(userUuid, "phone", "phone-thumb", "phone-content", "PHONE", baseTime.plusMinutes(1), null);
        insertGalleryItem(userUuid, "community_memo", "community-thumb", null, null, baseTime.plusMinutes(2), null);
        insertGalleryItem(userUuid, "infinite_canvas", "canvas-thumb", "canvas-content", "CANVAS",
            baseTime.plusMinutes(3), null);
        insertGalleryItem(userUuid, "flipbook", "flipbook-thumb", "flipbook-content", "ROOM-F", baseTime.plusMinutes(4),
            null);
        insertGalleryItem(userUuid, "relay_drawing", "relay-thumb", "relay-content", "ROOM-R", baseTime.plusMinutes(5),
            null);
        insertGalleryItem(userUuid, "fortune", "fortune-thumb", "fortune-content", null, baseTime.plusMinutes(6), null);

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("내 갤러리 목록 조회 성공")).andExpect(jsonPath("$.data.items", hasSize(6)))
            .andExpect(jsonPath("$.data.items[0].kind").value("fortune"))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value(publicUrl("fortune-thumb")))
            .andExpect(jsonPath("$.data.items[0].contentUrl").value(publicUrl("fortune-content")))
            .andExpect(jsonPath("$.data.items[1].kind").value("relay_drawing"))
            .andExpect(jsonPath("$.data.items[1].contentUrl").value(publicUrl("relay-content")))
            .andExpect(jsonPath("$.data.items[2].kind").value("flipbook"))
            .andExpect(jsonPath("$.data.items[2].contentUrl").value(publicUrl("flipbook-content")))
            .andExpect(jsonPath("$.data.items[3].kind").value("infinite_canvas"))
            .andExpect(jsonPath("$.data.items[3].contentUrl").value(publicUrl("canvas-content")))
            .andExpect(jsonPath("$.data.items[4].kind").value("community_memo"))
            .andExpect(jsonPath("$.data.items[4].contentUrl").value(publicUrl("community-thumb")))
            .andExpect(jsonPath("$.data.items[5].kind").value("phone"))
            .andExpect(jsonPath("$.data.items[5].contentUrl").value(publicUrl("phone-content")))
            .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(6)).andExpect(jsonPath("$.data.hasNext").value(false));
    }

    /**
     * 사용자는 존재하지만 보관 결과물이 없으면 빈 목록을 반환하는지 검증합니다.
     */
    @Test
    void getMyGalleryReturnsEmptyItemsWhenUserHasNoGalleryRows() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)))
            .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.hasNext").value(false));
    }

    /**
     * soft delete된 갤러리 항목과 artifact가 없는 비정상 행은 목록에서 제외되는지 검증합니다.
     */
    @Test
    void getMyGalleryExcludesSoftDeletedAndOrphanGalleryRows() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        insertGalleryItem(userUuid, "fortune", "active-thumb", "active-content", null, now, null);
        insertGalleryItem(userUuid, "phone", "deleted-thumb", "deleted-content", null, now.plusMinutes(1), now);
        insertGalleryOnly(UUID.randomUUID(), userUuid, UUID.randomUUID(), null);

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value(publicUrl("active-thumb")))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    /**
     * subtype row나 subtype URL이 없어도 thumbnailUrl fallback으로 목록 조회가 실패하지 않는지 검증합니다.
     */
    @Test
    void getMyGalleryFallsBackToThumbnailWhenSubtypeUrlIsMissing() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        insertArtifact(artifactId, "fortune", "fallback-thumb", null, now);
        insertGalleryOnly(galleryId, userUuid, artifactId, null);

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].contentUrl").value(publicUrl("fallback-thumb")));
    }

    /**
     * 상세 조회가 소유자의 active gallery row에 대해 URL과 JSON 메타데이터를 함께 반환하는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailReturnsArtifactDetailWithParsedMeta() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        GalleryTestRow row = insertGalleryItemWithMeta(userUuid, "fortune", "fortune-thumb", "fortune-content", null,
            createdAt, null, "{\"title\":\"오늘의 운세\",\"score\":88}");

        mockMvc
            .perform(get("/api/v1/gallery/{galleryId}", row.galleryId()).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("내 갤러리 항목 상세 조회 성공"))
            .andExpect(jsonPath("$.data.galleryId").value(row.galleryId().toString()))
            .andExpect(jsonPath("$.data.artifactId").value(row.artifactId().toString()))
            .andExpect(jsonPath("$.data.kind").value("fortune"))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl("fortune-thumb")))
            .andExpect(jsonPath("$.data.contentUrl").value(publicUrl("fortune-content")))
            .andExpect(jsonPath("$.data.sourceRoomId").doesNotExist())
            .andExpect(jsonPath("$.data.meta.title").value("오늘의 운세")).andExpect(jsonPath("$.data.meta.score").value(88))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty()).andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    /**
     * 결과물 종류별 대표 contentUrl이 subtype 테이블의 URL로 매핑되는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailMapsContentUrlByArtifactKind() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        GalleryTestRow fortune = insertGalleryItem(userUuid, "fortune", "fortune-thumb", "fortune-content", null, now,
            null);
        GalleryTestRow relay = insertGalleryItem(userUuid, "relay_drawing", "relay-thumb", "relay-content", null, now,
            null);
        GalleryTestRow flipbook = insertGalleryItem(userUuid, "flipbook", "flipbook-thumb", "flipbook-content", null,
            now, null);
        GalleryTestRow canvas = insertGalleryItem(userUuid, "infinite_canvas", "canvas-thumb", "canvas-content", null,
            now, null);
        GalleryTestRow phone = insertGalleryItem(userUuid, "phone", "phone-thumb", "phone-content", null, now, null);
        GalleryTestRow community = insertGalleryItem(userUuid, "community_memo", "community-thumb", null, null, now,
            null);

        assertDetailContentUrl(userUuid, fortune.galleryId(), publicUrl("fortune-content"));
        assertDetailContentUrl(userUuid, relay.galleryId(), publicUrl("relay-content"));
        assertDetailContentUrl(userUuid, flipbook.galleryId(), publicUrl("flipbook-content"));
        assertDetailContentUrl(userUuid, canvas.galleryId(), publicUrl("canvas-content"));
        assertDetailContentUrl(userUuid, phone.galleryId(), publicUrl("phone-content"));
        assertDetailContentUrl(userUuid, community.galleryId(), publicUrl("community-thumb"));
    }

    /**
     * subtype row가 없거나 subtype URL이 비어 있어도 상세 조회가 thumbnailUrl로 fallback되는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailFallsBackToThumbnailWhenSubtypeDataIsMissing() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactWithoutSubtypeId = UUID.randomUUID();
        UUID galleryWithoutSubtypeId = UUID.randomUUID();

        insertArtifact(artifactWithoutSubtypeId, "fortune", "no-subtype-thumb", null, now);
        insertGalleryOnly(galleryWithoutSubtypeId, userUuid, artifactWithoutSubtypeId, null);
        GalleryTestRow rowWithNullSubtypeUrl = insertGalleryItem(userUuid, "fortune", "null-url-thumb", null, null, now,
            null);

        assertDetailContentUrl(userUuid, galleryWithoutSubtypeId, publicUrl("no-subtype-thumb"));
        assertDetailContentUrl(userUuid, rowWithNullSubtypeUrl.galleryId(), publicUrl("null-url-thumb"));
    }

    /**
     * meta가 비어 있거나 JSON 객체로 파싱할 수 없어도 빈 객체로 방어 응답하는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailReturnsEmptyMetaWhenMetaIsBlankOrInvalid() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        GalleryTestRow blankMetaRow = insertGalleryItemWithMeta(userUuid, "phone", "blank-meta-thumb", "blank-content",
            null, now, null, " ");
        GalleryTestRow invalidMetaRow = insertGalleryItemWithMeta(userUuid, "phone", "invalid-meta-thumb",
            "invalid-content", null, now, null, "{not-json");

        assertDetailMetaIsEmpty(userUuid, blankMetaRow.galleryId());
        assertDetailMetaIsEmpty(userUuid, invalidMetaRow.galleryId());
    }

    /**
     * 상세 조회 성공 시 사용자, artifact, subtype, community_memo row가 변경되지 않는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailDoesNotUpdateUserArtifactSubtypeOrCommunityMemo() throws Exception {
        UUID userUuid = createExistingUser();
        AppUser beforeUser = userRepository.findById(userUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUserUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();
        GalleryTestRow row = insertGalleryItem(userUuid, "phone", "phone-thumb", "phone-content", null,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null);
        UUID memoId = insertCommunityMemo(userUuid, row.artifactId());
        LocalDateTime beforeArtifactUpdatedAt = findArtifactUpdatedAt(row.artifactId());

        mockMvc.perform(
            get("/api/v1/gallery/{galleryId}", row.galleryId()).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk());

        AppUser afterUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUserUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(findArtifactUpdatedAt(row.artifactId())).isEqualTo(beforeArtifactUpdatedAt);
        assertThat(findPhoneImageUrl(row.artifactId())).isEqualTo("phone-content");
        assertThat(countCommunityMemoRows(memoId)).isEqualTo(1);
        assertThat(findCommunityMemoDeletedAt(memoId)).isNull();
    }

    /**
     * userUuid나 galleryId 형식이 잘못되면 각각의 400 메시지를 반환하는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailRejectsInvalidUuidValues() throws Exception {
        mockMvc.perform(get("/api/v1/gallery/{galleryId}", UUID.randomUUID())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(
                get("/api/v1/gallery/{galleryId}", UUID.randomUUID()).header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(get("/api/v1/gallery/{galleryId}", "not-a-gallery-id").header(ANONYMOUS_USER_UUID_HEADER,
                UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 갤러리 항목 ID 형식입니다."));
    }

    /**
     * 존재하지 않는 사용자 UUID로 상세 조회하면 새 사용자를 만들지 않고 404를 반환하는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailReturnsNotFoundForMissingUserAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/gallery/{galleryId}", UUID.randomUUID()).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * 없는 항목, 타인 항목, 이미 삭제된 항목은 모두 존재하지 않는 갤러리 항목으로 처리하는지 검증합니다.
     */
    @Test
    void getMyGalleryItemDetailReturnsNotFoundForUnavailableGalleryRows() throws Exception {
        UUID userUuid = createExistingUser();
        UUID otherUserUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        GalleryTestRow otherUserRow = insertGalleryItem(otherUserUuid, "fortune", "other-thumb", "other-content", null,
            now, null);
        GalleryTestRow deletedRow = insertGalleryItem(userUuid, "phone", "deleted-thumb", "deleted-content", null, now,
            now);

        assertGalleryItemDetailNotFound(userUuid, UUID.randomUUID());
        assertGalleryItemDetailNotFound(userUuid, otherUserRow.galleryId());
        assertGalleryItemDetailNotFound(userUuid, deletedRow.galleryId());
    }

    /**
     * 페이지 번호, 크기, 전체 개수, 다음 페이지 여부가 기대대로 계산되는지 검증합니다.
     */
    @Test
    void getMyGalleryReturnsPaginationMetadata() throws Exception {
        UUID userUuid = createExistingUser();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        insertGalleryItem(userUuid, "fortune", "first-thumb", "first-content", null, baseTime.plusMinutes(3), null);
        insertGalleryItem(userUuid, "phone", "second-thumb", "second-content", null, baseTime.plusMinutes(2), null);
        insertGalleryItem(userUuid, "community_memo", "third-thumb", null, null, baseTime.plusMinutes(1), null);

        mockMvc
            .perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value(publicUrl("first-thumb")))
            .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc
            .perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).param("page", "1")
                .param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value(publicUrl("third-thumb")))
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.hasNext").value(false));
    }

    /**
     * 조회 성공 시 app_user의 방문/수정 메타데이터가 변경되지 않는지 검증합니다.
     */
    @Test
    void getMyGalleryDoesNotUpdateUserMetadata() throws Exception {
        UUID userUuid = createExistingUser();
        AppUser beforeUser = userRepository.findById(userUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        insertGalleryItem(userUuid, "fortune", "fortune-thumb", "fortune-content", null, LocalDateTime.now(), null);

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk());

        AppUser afterUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
    }

    /**
     * 갤러리 삭제는 보관 관계만 soft delete하고 원본 결과물과 커뮤니티 게시 메모는 유지하는지 검증합니다.
     */
    @Test
    void deleteMyGalleryItemSoftDeletesGalleryOnly() throws Exception {
        UUID userUuid = createExistingUser();
        GalleryTestRow row = insertGalleryItem(userUuid, "phone", "phone-thumb", "phone-content", null,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null);
        UUID memoId = insertCommunityMemo(userUuid, row.artifactId());
        LocalDateTime beforeArtifactUpdatedAt = findArtifactUpdatedAt(row.artifactId());

        mockMvc
            .perform(delete("/api/v1/gallery/{galleryId}", row.galleryId()).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("갤러리 항목 삭제 성공"))
            .andExpect(jsonPath("$.data.galleryId").value(row.galleryId().toString()))
            .andExpect(jsonPath("$.data.artifactId").value(row.artifactId().toString()))
            .andExpect(jsonPath("$.data.deletedAt").isNotEmpty());

        assertThat(findGalleryDeletedAt(row.galleryId())).isNotNull();
        assertThat(findArtifactUpdatedAt(row.artifactId())).isEqualTo(beforeArtifactUpdatedAt);
        assertThat(findPhoneImageUrl(row.artifactId())).isEqualTo("phone-content");
        assertThat(countCommunityMemoRows(memoId)).isEqualTo(1);
        assertThat(findCommunityMemoDeletedAt(memoId)).isNull();
    }

    /**
     * 삭제된 갤러리 항목은 기존 목록 조회에서 제외되는지 검증합니다.
     */
    @Test
    void deleteMyGalleryItemExcludesItemFromGalleryList() throws Exception {
        UUID userUuid = createExistingUser();
        GalleryTestRow row = insertGalleryItem(userUuid, "fortune", "fortune-thumb", "fortune-content", null,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null);

        mockMvc.perform(delete("/api/v1/gallery/{galleryId}", row.galleryId()).header(ANONYMOUS_USER_UUID_HEADER,
            userUuid.toString())).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)))
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * userUuid나 galleryId 형식이 잘못되면 각각의 400 메시지를 반환하는지 검증합니다.
     */
    @Test
    void deleteMyGalleryItemRejectsInvalidUuidValues() throws Exception {
        mockMvc.perform(delete("/api/v1/gallery/{galleryId}", UUID.randomUUID())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(delete("/api/v1/gallery/{galleryId}", UUID.randomUUID()).header(ANONYMOUS_USER_UUID_HEADER,
                "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc
            .perform(delete("/api/v1/gallery/{galleryId}", "not-a-gallery-id").header(ANONYMOUS_USER_UUID_HEADER,
                UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 갤러리 항목 ID 형식입니다."));
    }

    /**
     * 존재하지 않는 사용자 UUID로 삭제를 요청하면 새 사용자를 만들지 않고 404를 반환하는지 검증합니다.
     */
    @Test
    void deleteMyGalleryItemReturnsNotFoundForMissingUserAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(delete("/api/v1/gallery/{galleryId}", UUID.randomUUID()).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * 없는 항목, 타인 항목, 이미 삭제된 항목은 모두 존재하지 않는 갤러리 항목으로 처리하는지 검증합니다.
     */
    @Test
    void deleteMyGalleryItemReturnsNotFoundForUnavailableGalleryRows() throws Exception {
        UUID userUuid = createExistingUser();
        UUID otherUserUuid = createExistingUser();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        GalleryTestRow otherUserRow = insertGalleryItem(otherUserUuid, "fortune", "other-thumb", "other-content", null,
            now, null);
        GalleryTestRow deletedRow = insertGalleryItem(userUuid, "phone", "deleted-thumb", "deleted-content", null, now,
            now);

        assertGalleryItemNotFound(userUuid, UUID.randomUUID());
        assertGalleryItemNotFound(userUuid, otherUserRow.galleryId());
        assertGalleryItemNotFound(userUuid, deletedRow.galleryId());
    }

    /**
     * UUID가 없거나 형식이 잘못되면 400 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void getMyGalleryRejectsMissingOrInvalidUuidAndDoesNotCreateUser() throws Exception {
        mockMvc.perform(get("/api/v1/gallery")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
    }

    /**
     * UUID 형식은 맞지만 사용자가 없으면 404 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void getMyGalleryReturnsNotFoundAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * page가 음수이거나 size가 범위를 벗어나면 400 응답을 반환하는지 검증합니다.
     */
    @Test
    void getMyGalleryRejectsInvalidPagination() throws Exception {
        UUID userUuid = createExistingUser();

        assertInvalidPagination(userUuid, "-1", "20");
        assertInvalidPagination(userUuid, "0", "0");
        assertInvalidPagination(userUuid, "0", "51");
        assertInvalidPagination(userUuid, "zero", "20");
    }

    private void assertInvalidPagination(UUID userUuid, String page, String size) throws Exception {
        mockMvc
            .perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).param("page", page)
                .param("size", size))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("페이지 요청 값이 올바르지 않습니다."));
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private GalleryTestRow insertGalleryItem(UUID userUuid, String kind, String thumbnailUrl, String contentUrl,
        String sourceRoomId, LocalDateTime createdAt, LocalDateTime deletedAt) {
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        insertArtifact(artifactId, kind, thumbnailUrl, sourceRoomId, createdAt);
        insertSubtypeArtifact(kind, artifactId, contentUrl);
        insertGalleryOnly(galleryId, userUuid, artifactId, deletedAt);

        return new GalleryTestRow(galleryId, artifactId);
    }

    private GalleryTestRow insertGalleryItemWithMeta(UUID userUuid, String kind, String thumbnailUrl, String contentUrl,
        String sourceRoomId, LocalDateTime createdAt, LocalDateTime deletedAt, String meta) {
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        insertArtifact(artifactId, kind, thumbnailUrl, sourceRoomId, createdAt, meta);
        insertSubtypeArtifact(kind, artifactId, contentUrl);
        insertGalleryOnly(galleryId, userUuid, artifactId, deletedAt);

        return new GalleryTestRow(galleryId, artifactId);
    }

    private void insertArtifact(UUID artifactId, String kind, String thumbnailUrl, String sourceRoomId,
        LocalDateTime createdAt) {
        insertArtifact(artifactId, kind, thumbnailUrl, sourceRoomId, createdAt, "{}");
    }

    private void insertArtifact(UUID artifactId, String kind, String thumbnailUrl, String sourceRoomId,
        LocalDateTime createdAt, String meta) {
        String sql = """
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, artifactId, kind, sourceRoomId, thumbnailUrl, meta, createdAt, createdAt);
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

    private void insertGalleryOnly(UUID galleryId, UUID userUuid, UUID artifactId, LocalDateTime deletedAt) {
        jdbcTemplate.update("INSERT INTO gallery (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, ?)", galleryId,
            userUuid, artifactId, deletedAt);
    }

    private UUID insertCommunityMemo(UUID userUuid, UUID artifactId) {
        UUID memoId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO community_memo (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, NULL)",
            memoId, userUuid, artifactId);

        return memoId;
    }

    private void assertGalleryItemNotFound(UUID userUuid, UUID galleryId) throws Exception {
        mockMvc
            .perform(delete("/api/v1/gallery/{galleryId}", galleryId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));
    }

    private void assertGalleryItemDetailNotFound(UUID userUuid, UUID galleryId) throws Exception {
        mockMvc
            .perform(
                get("/api/v1/gallery/{galleryId}", galleryId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 갤러리 항목입니다."));
    }

    private void assertDetailContentUrl(UUID userUuid, UUID galleryId, String expectedContentUrl) throws Exception {
        mockMvc
            .perform(
                get("/api/v1/gallery/{galleryId}", galleryId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentUrl").value(expectedContentUrl));
    }

    private void assertDetailMetaIsEmpty(UUID userUuid, UUID galleryId) throws Exception {
        mockMvc
            .perform(
                get("/api/v1/gallery/{galleryId}", galleryId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.meta").value(anEmptyMap()));
    }

    private String publicUrl(String objectKey) {
        return MINIO_PUBLIC_URL + objectKey;
    }

    private LocalDateTime findGalleryDeletedAt(UUID galleryId) {
        return jdbcTemplate.queryForObject("SELECT deleted_at FROM gallery WHERE id = ?", (resultSet, rowNumber) -> {
            Timestamp timestamp = resultSet.getTimestamp("deleted_at");
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }, galleryId);
    }

    private LocalDateTime findArtifactUpdatedAt(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT updated_at FROM artifact WHERE id = ?",
            (resultSet, rowNumber) -> resultSet.getTimestamp("updated_at").toLocalDateTime(), artifactId);
    }

    private String findPhoneImageUrl(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT phone_image_url FROM phone_artifact WHERE artifact_id = ?",
            String.class, artifactId);
    }

    private int countCommunityMemoRows(UUID memoId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_memo WHERE id = ?", Integer.class,
            memoId);

        return count == null ? 0 : count;
    }

    private LocalDateTime findCommunityMemoDeletedAt(UUID memoId) {
        return jdbcTemplate.queryForObject("SELECT deleted_at FROM community_memo WHERE id = ?",
            (resultSet, rowNumber) -> {
                Timestamp timestamp = resultSet.getTimestamp("deleted_at");
                return timestamp == null ? null : timestamp.toLocalDateTime();
            }, memoId);
    }

    private record GalleryTestRow(UUID galleryId, UUID artifactId) {
    }
}
