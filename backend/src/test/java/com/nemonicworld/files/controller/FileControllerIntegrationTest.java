package com.nemonicworld.files.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
    "nemonic.storage.minio.presign-expiration-minutes=10", "nemonic.storage.minio.max-upload-byte-size=52428800"})
@Sql(statements = {"DELETE FROM file_upload", "DELETE FROM app_user"})
/**
 * MinIO 직접 업로드를 위한 presigned URL 발급 API를 검증합니다.
 */
class FileControllerIntegrationTest {

    private static final String USER_UUID_HEADER = "X-User-UUID";
    private static final String PRESIGNED_URL = "http://localhost:9000/nemonic-local/uploads/example.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MinioClient minioClient;

    /**
     * 정상 요청이면 pending 업로드 메타데이터를 저장하고 presigned PUT URL 정보를 반환합니다.
     */
    @Test
    void presignReturnsUrlAndPersistsPendingUpload() throws Exception {
        UUID userUuid = createExistingUser();
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).willReturn(PRESIGNED_URL);

        MvcResult result = mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("drawing.png", "image/png", "FLIPBOOK")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Presigned URL 발급 성공")).andExpect(jsonPath("$.data.fileId").exists())
            .andExpect(jsonPath("$.data.presignedUrl").value(PRESIGNED_URL))
            .andExpect(jsonPath("$.data.expiresIn").value(600)).andReturn();

        UUID fileId = UUID.fromString(readData(result).path("fileId").asText());
        assertThat(countFileUploads()).isEqualTo(1);
        assertThat(readStringColumn(fileId, "purpose")).isEqualTo("FLIPBOOK");
        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
        assertThat(readStringColumn(fileId, "original_file_name")).isEqualTo("drawing.png");
        assertThat(readStringColumn(fileId, "content_type")).isEqualTo("image/png");
        assertThat(readLongColumn(fileId, "byte_size")).isEqualTo(1024L);
        assertThat(readStringColumn(fileId, "object_key")).startsWith("uploads/flipbook/")
            .endsWith("/%s/drawing.png".formatted(fileId));
    }

    /**
     * purpose는 Java enum 이름과 같은 대문자 값만 허용합니다.
     */
    @Test
    void presignRejectsLowercasePurpose() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("phone.webp", "image/webp", "phone")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("지원하지 않는 purpose입니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 지원하지 않는 이미지 형식이면 400으로 응답하고 저장하지 않습니다.
     */
    @Test
    void presignRejectsUnsupportedContentType() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("drawing.svg", "image/svg+xml", "RELAY_DRAWING")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 파일 크기가 0 이하이면 400으로 응답하고 저장하지 않습니다.
     */
    @Test
    void presignRejectsInvalidByteSize() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("drawing.png", "image/png", "COMMUNITY", 0L)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 크기가 올바르지 않습니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 파일 크기가 누락되면 400으로 응답하고 저장하지 않습니다.
     */
    @Test
    void presignRejectsMissingByteSize() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString()).content("""
                    {
                      "fileName": "drawing.png",
                      "contentType": "image/png",
                      "purpose": "COMMUNITY"
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 크기가 올바르지 않습니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 파일 크기가 설정된 최대값을 넘으면 URL을 발급하지 않고 413으로 응답합니다.
     */
    @Test
    void presignRejectsTooLargeByteSize() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("huge.gif", "image/gif", "FLIPBOOK_GIF", 52428801L)))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 크기가 제한을 초과했습니다 (최대 50MB)."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 지원하지 않는 목적이면 400으로 응답하고 저장하지 않습니다.
     */
    @Test
    void presignRejectsUnsupportedPurpose() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("profile.png", "image/png", "PROFILE")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("지원하지 않는 purpose입니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 파일명에 경로 문자열이 있으면 object key 오염을 막기 위해 400으로 응답합니다.
     */
    @Test
    void presignRejectsUnsafeFileName() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("../drawing.png", "image/png", "COMMUNITY")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일명이 올바르지 않습니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * 사용자 UUID 헤더가 없거나 형식이 틀리면 400으로 응답합니다.
     */
    @Test
    void presignRejectsInvalidUserUuidHeader() throws Exception {
        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, "not-a-uuid")
                .content(presignRequestBody("drawing.png", "image/png", "COMMUNITY")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * UUID 형식은 맞지만 서버에 사용자가 없으면 404로 응답합니다.
     */
    @Test
    void presignReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, missingUserUuid.toString())
                .content(presignRequestBody("drawing.png", "image/png", "COMMUNITY")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * JSON 본문 자체가 깨진 경우도 전역 예외 처리에서 400으로 변환합니다.
     */
    @Test
    void presignRejectsMalformedJsonAsBadRequest() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(USER_UUID_HEADER, userUuid.toString()).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));

        assertThat(countFileUploads()).isZero();
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));
        return userUuid;
    }

    private String presignRequestBody(String fileName, String contentType, String purpose) {
        return presignRequestBody(fileName, contentType, purpose, 1024L);
    }

    private String presignRequestBody(String fileName, String contentType, String purpose, long byteSize) {
        return """
            {
              "fileName": "%s",
              "contentType": "%s",
              "purpose": "%s",
              "byteSize": %d
            }
            """.formatted(fileName, contentType, purpose, byteSize);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long countFileUploads() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_upload", Long.class);
    }

    private String readStringColumn(UUID fileId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM file_upload WHERE id = ?".formatted(columnName),
            String.class, fileId);
    }

    private long readLongColumn(UUID fileId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM file_upload WHERE id = ?".formatted(columnName), Long.class,
            fileId);
    }
}
