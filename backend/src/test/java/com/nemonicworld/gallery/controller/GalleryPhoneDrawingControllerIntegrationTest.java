package com.nemonicworld.gallery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.support.AppUserTestFixture;
import com.nemonicworld.support.ArtifactGalleryTestFixture;
import com.nemonicworld.support.ArtifactSubtypeTestFixture;
import com.nemonicworld.support.CommunityMemoTestFixture;
import com.nemonicworld.support.FileUploadTestFixture;
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
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
    "nemonic.storage.minio.public-url=http://localhost:9000", "nemonic.storage.minio.bucket=nemonic-local"})
class GalleryPhoneDrawingControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    private FileUploadTestFixture fileUploadFixture;

    @BeforeEach
    void prepareTables() {
        ArtifactGalleryTestFixture artifactGalleryFixture = new ArtifactGalleryTestFixture(jdbcTemplate);
        artifactGalleryFixture.ensureRelayArtifactTables();
        ArtifactSubtypeTestFixture artifactSubtypeFixture = new ArtifactSubtypeTestFixture(jdbcTemplate);
        artifactSubtypeFixture.ensureSubtypeTables();
        CommunityMemoTestFixture communityMemoFixture = new CommunityMemoTestFixture(jdbcTemplate);
        communityMemoFixture.ensureMemoTable();
        fileUploadFixture = new FileUploadTestFixture(jdbcTemplate);
        fileUploadFixture.ensureTable();

        communityMemoFixture.deleteMemoRows();
        artifactSubtypeFixture.deleteSubtypeRows();
        artifactGalleryFixture.deleteRelayArtifactRows();
        fileUploadFixture.deleteAll();
        new AppUserTestFixture(jdbcTemplate).deleteAll();
    }

    @Test
    void savePhoneDrawingCreatesPhoneArtifactAndGalleryItem() throws Exception {
        UUID userUuid = createExistingUser();
        UUID imageFileId = insertFileUpload(userUuid, "PHONE", "UPLOADED", imageObjectKey("original.png"), null);
        UUID thumbnailFileId = insertFileUpload(userUuid, "PHONE", "UPLOADED", imageObjectKey("thumbnail.png"), null);

        MvcResult result = mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .content(saveRequestBody(imageFileId, thumbnailFileId, """
                    {
                      "canvasWidth": 360,
                      "canvasHeight": 640,
                      "backgroundColor": "#ffffff"
                    }
                    """)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("휴대폰 그림 갤러리 저장 성공"))
            .andExpect(jsonPath("$.data.kind").value("phone"))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl(imageObjectKey("thumbnail.png"))))
            .andExpect(jsonPath("$.data.contentUrl").value(publicUrl(imageObjectKey("original.png"))))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty()).andReturn();

        JsonNode data = readData(result);
        UUID galleryId = UUID.fromString(data.path("galleryId").asText());
        UUID artifactId = UUID.fromString(data.path("artifactId").asText());

        assertThat(findArtifactKind(artifactId)).isEqualTo("phone");
        assertThat(findArtifactThumbnailUrl(artifactId)).isEqualTo(imageObjectKey("thumbnail.png"));
        assertThat(findPhoneImageUrl(artifactId)).isEqualTo(imageObjectKey("original.png"));
        assertThat(findGalleryUserId(galleryId)).isEqualTo(userUuid);
        assertThat(readArtifactMeta(artifactId).path("canvasWidth").asInt()).isEqualTo(360);

        mockMvc.perform(get("/api/v1/gallery").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].galleryId").value(galleryId.toString()))
            .andExpect(jsonPath("$.data.items[0].artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.items[0].kind").value("phone"))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value(publicUrl(imageObjectKey("thumbnail.png"))))
            .andExpect(jsonPath("$.data.items[0].contentUrl").value(publicUrl(imageObjectKey("original.png"))));

        mockMvc
            .perform(
                get("/api/v1/gallery/{galleryId}", galleryId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentUrl").value(publicUrl(imageObjectKey("original.png"))))
            .andExpect(jsonPath("$.data.meta.canvasHeight").value(640));

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("phone"))
            .andExpect(jsonPath("$.data.contents[0].type").value("phone_image"))
            .andExpect(jsonPath("$.data.contents[0].url").value(publicUrl(imageObjectKey("original.png"))));
    }

    @Test
    void savePhoneDrawingUsesImageAsThumbnailWhenThumbnailIsMissing() throws Exception {
        UUID userUuid = createExistingUser();
        UUID imageFileId = insertFileUpload(userUuid, "PHONE", "UPLOADED", imageObjectKey("single.png"), null);

        MvcResult result = mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .content(saveRequestBody(imageFileId, null, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl(imageObjectKey("single.png"))))
            .andExpect(jsonPath("$.data.contentUrl").value(publicUrl(imageObjectKey("single.png")))).andReturn();

        UUID artifactId = UUID.fromString(readData(result).path("artifactId").asText());
        assertThat(findArtifactThumbnailUrl(artifactId)).isEqualTo(imageObjectKey("single.png"));
        assertThat(readArtifactMeta(artifactId).isEmpty()).isTrue();
    }

    @Test
    void savePhoneDrawingRejectsInvalidUserUuid() throws Exception {
        mockMvc.perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
            .header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid").content(saveRequestBody(UUID.randomUUID(), null, null)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingReturnsNotFoundForMissingUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString())
                .content(saveRequestBody(UUID.randomUUID(), null, null)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingRejectsMissingOrInvalidImageFileId() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 imageFileId 형식입니다."));

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content("""
                    {
                      "imageFileId": "not-a-uuid"
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 imageFileId 형식입니다."));
    }

    @Test
    void savePhoneDrawingRejectsInvalidThumbnailFileId() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content("""
                    {
                      "imageFileId": "%s",
                      "thumbnailFileId": "not-a-uuid"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 thumbnailFileId 형식입니다."));
    }

    @Test
    void savePhoneDrawingReturnsNotFoundForMissingFileUpload() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .content(saveRequestBody(UUID.randomUUID(), null, null)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));

        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingRejectsOtherUsersFileUpload() throws Exception {
        UUID ownerUuid = createExistingUser();
        UUID requesterUuid = createExistingUser();
        UUID imageFileId = insertFileUpload(ownerUuid, "PHONE", "UPLOADED", imageObjectKey("other-user.png"), null);

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, requesterUuid.toString())
                .content(saveRequestBody(imageFileId, null, null)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));

        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingRejectsNonPhonePurposeFileUpload() throws Exception {
        UUID userUuid = createExistingUser();
        UUID imageFileId = insertFileUpload(userUuid, "COMMUNITY", "UPLOADED", imageObjectKey("community.png"), null);

        mockMvc.perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
            .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content(saveRequestBody(imageFileId, null, null)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("휴대폰 그림 파일만 저장할 수 있습니다."));

        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingRejectsUnavailableFileUploadStatus() throws Exception {
        UUID userUuid = createExistingUser();
        UUID pendingFileId = insertFileUpload(userUuid, "PHONE", "PENDING", imageObjectKey("pending.png"), null);
        UUID deletedFileId = insertFileUpload(userUuid, "PHONE", "DELETED", imageObjectKey("deleted.png"),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        assertUnavailableFileUpload(userUuid, pendingFileId);
        assertUnavailableFileUpload(userUuid, deletedFileId);
        assertThat(countArtifacts()).isZero();
    }

    @Test
    void savePhoneDrawingRejectsNonObjectMeta() throws Exception {
        UUID userUuid = createExistingUser();
        UUID imageFileId = insertFileUpload(userUuid, "PHONE", "UPLOADED", imageObjectKey("meta.png"), null);

        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .content(saveRequestBody(imageFileId, null, "[\"not\", \"object\"]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("메타데이터 형식이 올바르지 않습니다."));

        assertThat(countArtifacts()).isZero();
    }

    private void assertUnavailableFileUpload(UUID userUuid, UUID fileId) throws Exception {
        mockMvc
            .perform(post("/api/v1/gallery/drawings").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content(saveRequestBody(fileId, null, null)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private UUID insertFileUpload(UUID userUuid, String purpose, String status, String objectKey,
        LocalDateTime deletedAt) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return fileUploadFixture.insert(userUuid, purpose, "drawing.png", "image/png", 1024, objectKey, status,
            now.plusMinutes(10), now, now, deletedAt);
    }

    private String saveRequestBody(UUID imageFileId, UUID thumbnailFileId, String metaJson) {
        StringBuilder body = new StringBuilder("""
            {
              "imageFileId": "%s"
            """.formatted(imageFileId));
        if (thumbnailFileId != null) {
            body.append("""
                ,
                  "thumbnailFileId": "%s"
                """.formatted(thumbnailFileId));
        }
        if (metaJson != null) {
            body.append("""
                ,
                  "meta": %s
                """.formatted(metaJson));
        }
        body.append("""

            }
            """);

        return body.toString();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode readArtifactMeta(UUID artifactId) throws Exception {
        String meta = jdbcTemplate.queryForObject("SELECT meta FROM artifact WHERE id = ?", String.class, artifactId);

        return objectMapper.readTree(meta);
    }

    private String findArtifactKind(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT CAST(kind AS VARCHAR) FROM artifact WHERE id = ?", String.class,
            artifactId);
    }

    private String findArtifactThumbnailUrl(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT thumbnail_url FROM artifact WHERE id = ?", String.class, artifactId);
    }

    private String findPhoneImageUrl(UUID artifactId) {
        return jdbcTemplate.queryForObject("SELECT phone_image_url FROM phone_artifact WHERE artifact_id = ?",
            String.class, artifactId);
    }

    private UUID findGalleryUserId(UUID galleryId) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM gallery WHERE id = ?",
            (resultSet, rowNumber) -> resultSet.getObject("user_id", UUID.class), galleryId);
    }

    private long countArtifacts() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artifact", Long.class);

        return count == null ? 0L : count;
    }

    private String imageObjectKey(String fileName) {
        return "phone/results/test/%s".formatted(fileName);
    }

    private String publicUrl(String objectKey) {
        return "http://localhost:9000/nemonic-local/%s".formatted(objectKey);
    }
}
