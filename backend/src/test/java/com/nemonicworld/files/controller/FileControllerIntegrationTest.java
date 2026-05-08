package com.nemonicworld.files.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
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
    "nemonic.storage.minio.public-url=http://localhost:9000/minio",
    "nemonic.storage.minio.presign-expiration-minutes=10", "nemonic.storage.minio.view-url-expiration-minutes=1440",
    "nemonic.storage.minio.max-upload-byte-size=52428800"})
@Sql(statements = {"DELETE FROM file_upload", "DELETE FROM app_user"})
/**
 * MinIO 직접 업로드 파일 API를 검증합니다.
 */
class FileControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String SIGNED_PRESIGNED_URL = "http://localhost:9000/nemonic-local/uploads/example.png";
    private static final String SIGNED_VIEW_URL = "http://localhost:9000/nemonic-local/uploads/example.png"
        + "?X-Amz-Signature=view";
    private static final String PRESIGNED_URL = "http://localhost:9000/minio/nemonic-local/uploads/example.png";
    private static final String VIEW_URL = "http://localhost:9000/minio/nemonic-local/uploads/example.png"
        + "?X-Amz-Signature=view";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean(name = "minioClient")
    private MinioClient minioClient;

    @MockitoBean(name = "publicMinioClient")
    private MinioClient publicMinioClient;

    /**
     * 정상 요청이면 pending 업로드 메타데이터를 저장하고 presigned PUT URL 정보를 반환합니다.
     */
    @Test
    void presignReturnsUrlAndPersistsPendingUpload() throws Exception {
        UUID userUuid = createExistingUser();
        given(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .willReturn(SIGNED_PRESIGNED_URL);

        MvcResult result = mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
     * 업로드 완료된 파일이면 private 객체 조회를 위한 GET presigned URL을 발급합니다.
     */
    @Test
    void presignPhoneUsesResultStyleObjectKey() throws Exception {
        UUID userUuid = createExistingUser();
        given(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .willReturn(SIGNED_PRESIGNED_URL);

        MvcResult result = mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .content(presignRequestBody("phone-drawing.png", "image/png", "PHONE")))
            .andExpect(status().isOk()).andReturn();

        UUID fileId = UUID.fromString(readData(result).path("fileId").asText());
        assertThat(readStringColumn(fileId, "object_key"))
            .isEqualTo("phone/results/%s/phone-drawing.png".formatted(fileId));
    }

    @Test
    void viewUrlReturnsPresignedGetUrlForUploadedFileOwner() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "UPLOADED", 1024L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(statObjectResponse(1024L));
        given(publicMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .willReturn(SIGNED_VIEW_URL);

        mockMvc
            .perform(
                get("/api/v1/files/{fileId}/view-url", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("파일 조회 URL 발급 성공"))
            .andExpect(jsonPath("$.data.fileId").value(fileId.toString()))
            .andExpect(jsonPath("$.data.viewUrl").value(VIEW_URL)).andExpect(jsonPath("$.data.expiresIn").value(86400));

        verify(minioClient).statObject(any(StatObjectArgs.class));
        verify(publicMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    /**
     * 조회 URL 요청의 fileId가 UUID 형식이 아니면 400으로 응답합니다.
     */
    @Test
    void viewUrlRejectsInvalidFileId() throws Exception {
        UUID userUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/files/not-a-uuid/view-url").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 fileId 형식입니다."));
    }

    /**
     * 다른 사용자의 업로드 파일 조회 URL은 발급하지 않습니다.
     */
    @Test
    void viewUrlRejectsFileUploadOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser();
        UUID requesterUuid = createExistingUser();
        UUID fileId = insertFileUpload(ownerUuid, "UPLOADED", 1024L);

        mockMvc
            .perform(get("/api/v1/files/{fileId}/view-url", fileId).header(ANONYMOUS_USER_UUID_HEADER,
                requesterUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));
    }

    /**
     * 업로드 완료 전 pending 파일은 아직 조회 URL을 발급하지 않습니다.
     */
    @Test
    void viewUrlRejectsPendingFileUpload() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);

        mockMvc
            .perform(
                get("/api/v1/files/{fileId}/view-url", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("조회할 수 없는 파일 업로드 상태입니다."));
    }

    /**
     * DB에는 업로드 완료로 남아 있어도 실제 MinIO object가 없으면 404로 응답합니다.
     */
    @Test
    void viewUrlReturnsNotFoundWhenMinioObjectDoesNotExist() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "UPLOADED", 1024L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(minioError("NoSuchKey"));

        mockMvc
            .perform(
                get("/api/v1/files/{fileId}/view-url", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));
    }

    /**
     * purpose는 Java enum 이름과 같은 대문자 값만 허용합니다.
     */
    @Test
    void presignRejectsLowercasePurpose() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/files/presign").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content("""
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid")
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
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString())
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
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));

        assertThat(countFileUploads()).isZero();
    }

    /**
     * MinIO에 객체가 실제로 있으면 pending 업로드를 uploaded 상태로 확정합니다.
     */
    @Test
    void confirmMarksPendingUploadAsUploadedWhenObjectExists() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(statObjectResponse(1024L));

        mockMvc
            .perform(
                post("/api/v1/files/{fileId}/confirm", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("파일 업로드 확인 성공"))
            .andExpect(jsonPath("$.data.fileId").value(fileId.toString()))
            .andExpect(jsonPath("$.data.status").value("UPLOADED"));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("UPLOADED");
    }

    /**
     * fileId가 UUID 형식이 아니면 400으로 응답합니다.
     */
    @Test
    void confirmRejectsInvalidFileId() throws Exception {
        UUID userUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/files/not-a-uuid/confirm").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 fileId 형식입니다."));
    }

    /**
     * file_upload 메타데이터가 없으면 404로 응답합니다.
     */
    @Test
    void confirmReturnsNotFoundWhenFileUploadDoesNotExist() throws Exception {
        UUID userUuid = createExistingUser();
        UUID missingFileId = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/files/{fileId}/confirm", missingFileId).header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));
    }

    /**
     * 다른 사용자의 파일 업로드는 확인할 수 없습니다.
     */
    @Test
    void confirmRejectsFileUploadOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser();
        UUID requesterUuid = createExistingUser();
        UUID fileId = insertFileUpload(ownerUuid, "PENDING", 1024L);

        mockMvc
            .perform(post("/api/v1/files/{fileId}/confirm", fileId).header(ANONYMOUS_USER_UUID_HEADER,
                requesterUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
    }

    /**
     * pending 상태가 아니면 중복 confirm을 막기 위해 409로 응답합니다.
     */
    @Test
    void confirmRejectsAlreadyUploadedFileUpload() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "UPLOADED", 1024L);

        mockMvc
            .perform(
                post("/api/v1/files/{fileId}/confirm", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("확인할 수 없는 파일 업로드 상태입니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("UPLOADED");
    }

    /**
     * pending 메타데이터는 있지만 MinIO object가 없으면 404로 응답합니다.
     */
    @Test
    void confirmReturnsNotFoundWhenMinioObjectDoesNotExist() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(minioError("NoSuchKey"));

        mockMvc
            .perform(
                post("/api/v1/files/{fileId}/confirm", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 업로드 정보를 찾을 수 없습니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
    }

    /**
     * 실제 MinIO object 크기가 정책보다 크면 uploaded 상태로 확정하지 않습니다.
     */
    @Test
    void confirmRejectsObjectLargerThanMaxUploadSize() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(statObjectResponse(52428801L));

        mockMvc
            .perform(
                post("/api/v1/files/{fileId}/confirm", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 크기가 제한을 초과했습니다 (최대 50MB)."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
    }

    /**
     * pending 업로드 삭제 요청이면 MinIO object 삭제 후 DB 메타데이터를 deleted 상태로 변경합니다.
     */
    @Test
    void deleteMarksPendingUploadAsDeleted() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);

        mockMvc
            .perform(delete("/api/v1/files/{fileId}", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("파일 삭제 성공"))
            .andExpect(jsonPath("$.data.fileId").value(fileId.toString()))
            .andExpect(jsonPath("$.data.status").value("DELETED"));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("DELETED");
        assertThat(readTimestampColumn(fileId, "deleted_at")).isNotNull();
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    /**
     * delete 요청의 fileId가 UUID 형식이 아니면 400으로 응답합니다.
     */
    @Test
    void deleteRejectsInvalidFileId() throws Exception {
        UUID userUuid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/files/not-a-uuid").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 fileId 형식입니다."));
    }

    /**
     * 다른 사용자의 pending 업로드는 삭제할 수 없습니다.
     */
    @Test
    void deleteRejectsFileUploadOwnedByAnotherUser() throws Exception {
        UUID ownerUuid = createExistingUser();
        UUID requesterUuid = createExistingUser();
        UUID fileId = insertFileUpload(ownerUuid, "PENDING", 1024L);

        mockMvc
            .perform(
                delete("/api/v1/files/{fileId}", fileId).header(ANONYMOUS_USER_UUID_HEADER, requesterUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일에 접근할 권한이 없습니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
    }

    /**
     * uploaded 상태의 파일은 이미 산출물/게시글과 연결될 수 있으므로 이 API에서 물리 삭제하지 않습니다.
     */
    @Test
    void deleteRejectsAlreadyUploadedFileUpload() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "UPLOADED", 1024L);

        mockMvc
            .perform(delete("/api/v1/files/{fileId}", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("삭제할 수 없는 파일 업로드 상태입니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("UPLOADED");
    }

    /**
     * MinIO object가 이미 없어도 pending 취소는 성공으로 보고 DB 상태를 deleted로 정리합니다.
     */
    @Test
    void deleteSucceedsWhenMinioObjectDoesNotExist() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);
        willThrow(minioError("NoSuchKey")).given(minioClient).removeObject(any(RemoveObjectArgs.class));

        mockMvc
            .perform(delete("/api/v1/files/{fileId}", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("파일 삭제 성공")).andExpect(jsonPath("$.data.status").value("DELETED"));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("DELETED");
        assertThat(readTimestampColumn(fileId, "deleted_at")).isNotNull();
    }

    /**
     * 권한/버킷 설정 같은 MinIO 오류는 파일 없음으로 숨기지 않고 서버 오류로 응답합니다.
     */
    @Test
    void deleteReturnsServerErrorWhenMinioRemoveFails() throws Exception {
        UUID userUuid = createExistingUser();
        UUID fileId = insertFileUpload(userUuid, "PENDING", 1024L);
        willThrow(minioError("AccessDenied")).given(minioClient).removeObject(any(RemoveObjectArgs.class));

        mockMvc
            .perform(delete("/api/v1/files/{fileId}", fileId).header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("파일 저장소 처리 중 오류가 발생했습니다."));

        assertThat(readStringColumn(fileId, "status")).isEqualTo("PENDING");
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

    private Timestamp readTimestampColumn(UUID fileId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM file_upload WHERE id = ?".formatted(columnName),
            Timestamp.class, fileId);
    }

    private UUID insertFileUpload(UUID userUuid, String status, long byteSize) {
        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String objectKey = "uploads/flipbook/2026/05/03/%s/drawing.png".formatted(fileId);

        jdbcTemplate.update("""
            INSERT INTO file_upload (
                id,
                user_id,
                purpose,
                original_file_name,
                content_type,
                byte_size,
                object_key,
                status,
                expires_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, 'FLIPBOOK', 'drawing.png', 'image/png', ?, ?, ?, ?, ?, ?)
            """, fileId, userUuid, byteSize, objectKey, status, Timestamp.valueOf(now.plusMinutes(10)),
            Timestamp.valueOf(now), Timestamp.valueOf(now));

        return fileId;
    }

    private StatObjectResponse statObjectResponse(long byteSize) {
        Headers headers = Headers.of("Content-Length", String.valueOf(byteSize), "Last-Modified",
            "Wed, 21 Oct 2015 07:28:00 GMT", "ETag", "\"etag\"");

        return new StatObjectResponse(headers, "nemonic-local", null, "object-key");
    }

    private ErrorResponseException minioError(String code) {
        ErrorResponse errorResponse = new ErrorResponse(code, "Object does not exist", "nemonic-local", "object-key",
            "/nemonic-local/object-key", "request-id", "host-id");
        Response response = new Response.Builder()
            .request(new Request.Builder().url("http://localhost:9000/nemonic-local/object-key").build())
            .protocol(Protocol.HTTP_1_1).code(404).message("Not Found").build();

        return new ErrorResponseException(errorResponse, response, null);
    }
}
