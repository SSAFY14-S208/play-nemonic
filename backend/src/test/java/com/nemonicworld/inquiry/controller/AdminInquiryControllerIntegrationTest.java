package com.nemonicworld.inquiry.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.common.exception.EmailDeliveryException;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.inquiry.service.InquiryMailSender;
import com.nemonicworld.support.AbstractReadOnlyIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.support.AppUserTestFixture;
import com.nemonicworld.support.BackofficeAuthTestFixture;
import com.nemonicworld.support.CsInquiryTestFixture;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@Import(AdminInquiryControllerIntegrationTest.InquiryMailSenderTestConfig.class)
class AdminInquiryControllerIntegrationTest extends AbstractReadOnlyIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final long SUPER_ADMIN_ID = 2L;
    private static final String ADMIN_LOGIN_ID = "inquiry-admin";
    private static final String SUPER_ADMIN_LOGIN_ID = "inquiry-super-admin";
    private static final String ADMIN_NICKNAME = "Inquiry Admin";
    private static final String ADMIN_EMAIL = "inquiry-admin@example.com";
    private static final String SUPER_ADMIN_EMAIL = "inquiry-super-admin@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FakeInquiryMailSender inquiryMailSender;

    private AdminUserTestFixture adminUserFixture;
    private AppUserTestFixture appUserFixture;
    private CsInquiryTestFixture csInquiryFixture;

    @BeforeEach
    void prepareTables() {
        adminUserFixture = new AdminUserTestFixture(jdbcTemplate);
        appUserFixture = new AppUserTestFixture(jdbcTemplate);
        csInquiryFixture = new CsInquiryTestFixture(jdbcTemplate);
        adminUserFixture.ensureTable();
        appUserFixture.ensureTable();
        csInquiryFixture.ensureTable();
        csInquiryFixture.deleteAll();
        appUserFixture.deleteAll();
        adminUserFixture.deleteAll();
        csInquiryFixture.restartIdentity(100);
        adminUserFixture.restartIdentity(100);
        insertAdminUser(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_EMAIL, AdminRole.ADMIN);
        insertAdminUser(SUPER_ADMIN_ID, SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_EMAIL, AdminRole.SUPER_ADMIN);
        inquiryMailSender.reset();
    }

    @Test
    void adminGetsInquiryList() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "이전 오류 문의", "이전 문의 내용", "old@example.com", "new", createdAt);
        insertInquiry(101L, userUuid, "other", "최근 기타 문의", "최근 문의 내용", "new@example.com", "resolved", createdAt);

        mockMvc.perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].id").value(101L))
            .andExpect(jsonPath("$.data.items[0].userId").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.items[0].type").value("other"))
            .andExpect(jsonPath("$.data.items[0].title").value("최근 기타 문의"))
            .andExpect(jsonPath("$.data.items[0].email").value("new@example.com"))
            .andExpect(jsonPath("$.data.items[0].status").value("resolved"))
            .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].attachments").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].meta").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].responseNote").doesNotExist())
            .andExpect(jsonPath("$.data.items[1].id").value(100L)).andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void superAdminGetsInquiryList() throws Exception {
        UUID userUuid = insertAppUser();
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken(SUPER_ADMIN_ID, SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_EMAIL, AdminRole.SUPER_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L));
    }

    @Test
    void inquiryListRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void inquiryListFiltersByStatus() throws Exception {
        UUID userUuid = insertAppUser();
        insertInquiry(100L, userUuid, "error", "신규 문의", "문의 내용", "new@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));
        insertInquiry(101L, userUuid, "error", "처리 완료 문의", "문의 내용", "resolved@example.com", "resolved",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("status", "new"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L))
            .andExpect(jsonPath("$.data.items[0].status").value("new"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void inquiryListFiltersByType() throws Exception {
        UUID userUuid = insertAppUser();
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "error@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));
        insertInquiry(101L, userUuid, "feature_request", "기능 제안", "문의 내용", "feature@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("type", "feature_request"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(101L))
            .andExpect(jsonPath("$.data.items[0].type").value("feature_request"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void inquiryListSearchesByKeyword() throws Exception {
        UUID userUuid = insertAppUser();
        insertInquiry(100L, userUuid, "error", "오류 문의", "결제가 완료되지 않았습니다.", "pay@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));
        insertInquiry(101L, userUuid, "other", "기타 문의", "다른 문의 내용", "other@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "결제"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L)).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void inquiryKeywordSearchTreatsLikeWildcardsAsLiteralText() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "Percent inquiry", "payment reached 100% mark", "percent@example.com",
            "new", createdAt);
        insertInquiry(101L, userUuid, "other", "Underscore inquiry", "under_score marker", "underscore@example.com",
            "new", createdAt);
        insertInquiry(102L, userUuid, "other", "Plain inquiry", "plain marker", "plain@example.com", "new", createdAt);

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L)).andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "_"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(101L)).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void inquiryListFiltersByUserUuid() throws Exception {
        UUID targetUserUuid = insertAppUser();
        UUID otherUserUuid = insertAppUser();
        insertInquiry(100L, targetUserUuid, "error", "대상 사용자 문의", "문의 내용", "target@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));
        insertInquiry(101L, otherUserUuid, "other", "다른 사용자 문의", "문의 내용", "other@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("userUuid", targetUserUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L))
            .andExpect(jsonPath("$.data.items[0].userId").value(targetUserUuid.toString()))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void inquiryListAppliesPagination() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "첫 번째 문의", "문의 내용", "first@example.com", "new", createdAt);
        insertInquiry(101L, userUuid, "error", "두 번째 문의", "문의 내용", "second@example.com", "new", createdAt);
        insertInquiry(102L, userUuid, "error", "세 번째 문의", "문의 내용", "third@example.com", "new", createdAt);

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("page", "1").queryParam("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(101L)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1)).andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @ParameterizedTest
    @CsvSource({"status,unknown", "type,unknown", "userUuid,not-a-uuid", "page,-1", "page,abc", "size,0", "size,51",
        "size,abc"})
    void inquiryListRejectsInvalidParameters(String parameterName, String parameterValue) throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam(parameterName, parameterValue))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminGetsInquiryDetail() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime respondedAt = createdAt.plusHours(1);
        insertInquiry(100L, userUuid, "error", "결제 오류 문의", "결제는 완료됐는데 서비스가 활성화되지 않았습니다.", "user@example.com",
            "in_progress", ADMIN_ID, "결제 내역 확인 중", respondedAt,
            "[\"https://cdn.example.com/1.png\",\"https://cdn.example.com/2.png\"]",
            "{\"userAgent\":\"MockMvc/1.0\",\"referer\":\"https://k14s208.p.ssafy.io/support\"}", createdAt);

        mockMvc
            .perform(
                get("/api/v1/admin/inquiries/{inquiryId}", 100L).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100L))
            .andExpect(jsonPath("$.data.userId").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.type").value("error")).andExpect(jsonPath("$.data.title").value("결제 오류 문의"))
            .andExpect(jsonPath("$.data.content").value("결제는 완료됐는데 서비스가 활성화되지 않았습니다."))
            .andExpect(jsonPath("$.data.email").value("user@example.com"))
            .andExpect(jsonPath("$.data.attachments.length()").value(2))
            .andExpect(jsonPath("$.data.attachments[0]").value("https://cdn.example.com/1.png"))
            .andExpect(jsonPath("$.data.meta.userAgent").value("MockMvc/1.0"))
            .andExpect(jsonPath("$.data.meta.referer").value("https://k14s208.p.ssafy.io/support"))
            .andExpect(jsonPath("$.data.status").value("in_progress"))
            .andExpect(jsonPath("$.data.assignedTo").value(ADMIN_ID))
            .andExpect(jsonPath("$.data.responseNote").value("결제 내역 확인 중"))
            .andExpect(jsonPath("$.data.respondedAt").isNotEmpty()).andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    void superAdminGetsInquiryDetail() throws Exception {
        UUID userUuid = insertAppUser();
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new",
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/admin/inquiries/{inquiryId}", 100L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken(SUPER_ADMIN_ID, SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_EMAIL, AdminRole.SUPER_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100L));
    }

    @Test
    void inquiryDetailRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries/{inquiryId}", 100L)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void inquiryDetailRejectsUnknownId() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/inquiries/{inquiryId}", 999L).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("고객 문의를 찾을 수 없습니다."));
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "abc"})
    void inquiryDetailRejectsInvalidId(String inquiryId) throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/inquiries/{inquiryId}", inquiryId).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("문의 ID가 올바르지 않습니다."));
    }

    @Test
    void inquiryDetailReturnsEmptyAttachmentsAndMetaWhenStoredValuesAreNull() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "other", "기타 문의", "문의 내용", "user@example.com", "new", null, null, null, null,
            null, createdAt);

        mockMvc
            .perform(
                get("/api/v1/admin/inquiries/{inquiryId}", 100L).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.attachments.length()").value(0))
            .andExpect(jsonPath("$.data.meta").value(aMapWithSize(0)));
    }

    @Test
    void adminRepliesInquiryEmail(CapturedOutput output) throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "결제 오류 문의", "문의 내용", "user@example.com", "new", null, null, null, null,
            null, createdAt);

        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).header("X-Trace-Id", "inquiry-reply-audit-test")
                .header("X-Real-IP", "10.10.40.11").contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변드립니다", "문의하신 결제 내역을 확인했습니다.")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100L)).andExpect(jsonPath("$.data.status").value("resolved"))
            .andExpect(jsonPath("$.data.respondedAt").isNotEmpty());

        assertThat(inquiryMailSender.to).isEqualTo("user@example.com");
        assertThat(inquiryMailSender.subject).isEqualTo("답변드립니다");
        assertThat(inquiryMailSender.message).isEqualTo("문의하신 결제 내역을 확인했습니다.");
        assertThat(readStringColumn(100L, "status")).isEqualTo("resolved");
        assertThat(readLongColumn(100L, "assigned_to")).isEqualTo(ADMIN_ID);
        assertThat(readStringColumn(100L, "response_note")).isEqualTo("문의하신 결제 내역을 확인했습니다.");
        assertThat(readTimestampColumn(100L, "responded_at")).isNotNull();

        JsonNode auditLog = findAuditLog(output, "inquiry_reply_send");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("level").asText()).isEqualTo("INFO");
        assertThat(auditLog.path("service").asText()).isEqualTo("backoffice-api");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("inquiry-reply-audit-test");
        assertThat(metadata.path("actor_id").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(metadata.path("actor_role").asText()).isEqualTo("admin");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.40.11");
        assertThat(metadata.path("target_type").asText()).isEqualTo("inquiry");
        assertThat(metadata.path("target_id").asText()).isEqualTo("100");
        assertThat(metadata.path("action").asText()).isEqualTo("send");
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("before").path("status").asText()).isEqualTo("new");
        assertThat(metadata.path("after").path("status").asText()).isEqualTo("resolved");
        assertThat(metadata.path("after").path("assigned_to").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(auditLog.toString()).doesNotContain(inquiryMailSender.subject, inquiryMailSender.message,
            "user@example.com", "문의 내용");
    }

    @Test
    void superAdminRepliesInquiryEmail() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new", null, null, null, null,
            null, createdAt);

        mockMvc
            .perform(
                post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                    .header(HttpHeaders.AUTHORIZATION,
                        bearerAccessToken(SUPER_ADMIN_ID, SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_EMAIL,
                            AdminRole.SUPER_ADMIN))
                    .contentType(MediaType.APPLICATION_JSON).content(replyRequestBody("답변", "슈퍼 관리자 답변입니다.")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("resolved"));

        assertThat(readLongColumn(100L, "assigned_to")).isEqualTo(SUPER_ADMIN_ID);
    }

    @Test
    void inquiryReplyRejectsUnauthenticatedRequest() throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", "문의 답변입니다.")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void inquiryReplyRejectsUnknownId() throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 999L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", "문의 답변입니다.")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("고객 문의를 찾을 수 없습니다."));
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "abc"})
    void inquiryReplyRejectsInvalidId(String inquiryId) throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", inquiryId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", "문의 답변입니다.")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("문의 ID가 올바르지 않습니다."));
    }

    @Test
    void inquiryReplyRejectsInquiryWithoutEmail() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "other", "기타 문의", "문의 내용", null, "new", null, null, null, null, null, createdAt);

        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", "문의 답변입니다.")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이메일이 없어 회신할 수 없습니다."));
    }

    @Test
    void inquiryReplyRejectsMissingSubjectAndMessage() throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.subject").value("이메일 제목을 입력해 주세요."))
            .andExpect(jsonPath("$.errors.message").value("회신 내용을 입력해 주세요."));
    }

    @Test
    void inquiryReplyRejectsBlankSubject() throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody(" ", "문의 답변입니다.")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.subject").value("이메일 제목을 입력해 주세요."));
    }

    @Test
    void inquiryReplyRejectsBlankMessage() throws Exception {
        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", " ")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.message").value("회신 내용을 입력해 주세요."));
    }

    @Test
    void inquiryReplyDoesNotResolveInquiryWhenEmailDeliveryFails() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new", null, null, null, null,
            null, createdAt);
        inquiryMailSender.failNext();

        mockMvc
            .perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(replyRequestBody("답변", "문의 답변입니다.")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이메일 발송에 실패했습니다."));

        assertThat(readStringColumn(100L, "status")).isEqualTo("new");
        assertThat(readLongColumn(100L, "assigned_to")).isNull();
        assertThat(readStringColumn(100L, "response_note")).isNull();
        assertThat(readTimestampColumn(100L, "responded_at")).isNull();
    }

    @Test
    void inquiryDetailShowsReplyResultAfterReply() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new", null, null, null, null,
            null, createdAt);

        mockMvc.perform(post("/api/v1/admin/inquiries/{inquiryId}/reply", 100L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
            .content(replyRequestBody("답변", "상세에서 보일 답변입니다."))).andExpect(status().isOk());

        mockMvc
            .perform(
                get("/api/v1/admin/inquiries/{inquiryId}", 100L).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("resolved"))
            .andExpect(jsonPath("$.data.assignedTo").value(ADMIN_ID))
            .andExpect(jsonPath("$.data.responseNote").value("상세에서 보일 답변입니다."))
            .andExpect(jsonPath("$.data.respondedAt").isNotEmpty());
    }

    @Test
    void adminUpdatesInquiryStatus(CapturedOutput output) throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime respondedAt = createdAt.plusHours(1);
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new", ADMIN_ID, "기존 답변",
            respondedAt, null, null, createdAt);

        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "inquiry-status-audit-test").header("X-Forwarded-For", "10.10.40.21, 10.10.40.22")
                .contentType(MediaType.APPLICATION_JSON).content(statusRequestBody("in_progress")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100L)).andExpect(jsonPath("$.data.status").value("in_progress"))
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(readStringColumn(100L, "status")).isEqualTo("in_progress");
        assertThat(readTimestampColumn(100L, "updated_at").toLocalDateTime()).isAfter(createdAt);
        assertThat(readLongColumn(100L, "assigned_to")).isEqualTo(ADMIN_ID);
        assertThat(readStringColumn(100L, "response_note")).isEqualTo("기존 답변");
        assertThat(readTimestampColumn(100L, "responded_at").toLocalDateTime()).isEqualTo(respondedAt);
        assertThat(inquiryMailSender.to).isNull();

        JsonNode auditLog = findAuditLog(output, "inquiry_status_change");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("level").asText()).isEqualTo("INFO");
        assertThat(auditLog.path("service").asText()).isEqualTo("backoffice-api");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("inquiry-status-audit-test");
        assertThat(metadata.path("actor_id").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(metadata.path("actor_role").asText()).isEqualTo("admin");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.40.21");
        assertThat(metadata.path("target_type").asText()).isEqualTo("inquiry");
        assertThat(metadata.path("target_id").asText()).isEqualTo("100");
        assertThat(metadata.path("action").asText()).isEqualTo("update");
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("before").path("status").asText()).isEqualTo("new");
        assertThat(metadata.path("after").path("status").asText()).isEqualTo("in_progress");
        assertThat(auditLog.toString()).doesNotContain("user@example.com", "문의 내용", "기존 답변");
    }

    @Test
    void superAdminUpdatesInquiryStatus() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "오류 문의", "문의 내용", "user@example.com", "new", createdAt);

        mockMvc
            .perform(
                patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
                    .header(HttpHeaders.AUTHORIZATION,
                        bearerAccessToken(SUPER_ADMIN_ID, SUPER_ADMIN_LOGIN_ID, SUPER_ADMIN_EMAIL,
                            AdminRole.SUPER_ADMIN))
                    .contentType(MediaType.APPLICATION_JSON).content(statusRequestBody("closed")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("closed"));

        assertThat(readStringColumn(100L, "status")).isEqualTo("closed");
    }

    @Test
    void inquiryStatusUpdateRejectsUnauthenticatedRequest() throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L).contentType(MediaType.APPLICATION_JSON)
                .content(statusRequestBody("in_progress")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void inquiryStatusUpdateRejectsUnknownId() throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 999L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(statusRequestBody("in_progress")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("고객 문의를 찾을 수 없습니다."));
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "abc"})
    void inquiryStatusUpdateRejectsInvalidId(String inquiryId) throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", inquiryId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(statusRequestBody("in_progress")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("문의 ID가 올바르지 않습니다."));
    }

    @Test
    void inquiryStatusUpdateRejectsMissingStatus() throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.status").value("문의 상태를 입력해 주세요."));
    }

    @Test
    void inquiryStatusUpdateRejectsBlankStatus() throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(statusRequestBody(" ")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.status").value("문의 상태를 입력해 주세요."));
    }

    @Test
    void inquiryStatusUpdateRejectsInvalidStatus() throws Exception {
        mockMvc
            .perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(statusRequestBody("unknown")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("문의 상태가 올바르지 않습니다."));
    }

    @Test
    void inquiryStatusUpdateIsReflectedInListAndDetail() throws Exception {
        UUID userUuid = insertAppUser();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertInquiry(100L, userUuid, "error", "상태 변경 문의", "문의 내용", "user@example.com", "new", createdAt);

        mockMvc.perform(patch("/api/v1/admin/inquiries/{inquiryId}/status", 100L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
            .content(statusRequestBody("resolved"))).andExpect(status().isOk());

        mockMvc
            .perform(
                get("/api/v1/admin/inquiries/{inquiryId}", 100L).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("resolved"));

        mockMvc
            .perform(get("/api/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("status", "resolved"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(100L))
            .andExpect(jsonPath("$.data.items[0].status").value("resolved"));
    }

    private void insertAdminUser(long id, String loginId, String email, AdminRole role) {
        adminUserFixture.insertEncoded(id, loginId, ADMIN_NICKNAME, email, role);
    }

    private UUID insertAppUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        appUserFixture.insertAnonymous(userUuid, "익명", "MockMvc/1.0", now, now, now);

        return userUuid;
    }

    private void insertInquiry(long id, UUID userUuid, String type, String title, String content, String email,
        String status, LocalDateTime createdAt) {
        insertInquiry(id, userUuid, type, title, content, email, status, null, "관리자 내부 메모", null,
            "[\"https://cdn.example.com/1.png\"]", "{\"source\":\"test\"}", createdAt);
    }

    private void insertInquiry(long id, UUID userUuid, String type, String title, String content, String email,
        String status, Long assignedTo, String responseNote, LocalDateTime respondedAt, String attachments, String meta,
        LocalDateTime createdAt) {
        csInquiryFixture.insert(id, userUuid, type, title, content, email, attachments, meta, status, assignedTo,
            responseNote, respondedAt, createdAt, createdAt);
    }

    private String bearerAccessToken() {
        return bearerAccessToken(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_EMAIL, AdminRole.ADMIN);
    }

    private String bearerAccessToken(long id, String loginId, String email, AdminRole role) {
        return BackofficeAuthTestFixture.bearerAccessToken(jwtTokenProvider, id, loginId, ADMIN_NICKNAME, email, role);
    }

    private String replyRequestBody(String subject, String message) {
        return """
            {
              "subject": "%s",
              "message": "%s"
            }
            """.formatted(subject, message);
    }

    private String statusRequestBody(String status) {
        return """
            {
              "status": "%s"
            }
            """.formatted(status);
    }

    private String readStringColumn(long inquiryId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM cs_inquiry WHERE id = ?".formatted(columnName), String.class,
            inquiryId);
    }

    private Long readLongColumn(long inquiryId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM cs_inquiry WHERE id = ?".formatted(columnName), Long.class,
            inquiryId);
    }

    private Timestamp readTimestampColumn(long inquiryId, String columnName) {
        return jdbcTemplate.queryForObject("SELECT %s FROM cs_inquiry WHERE id = ?".formatted(columnName),
            Timestamp.class, inquiryId);
    }

    private JsonNode findAuditLog(CapturedOutput output, String eventName) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            if (line.contains("\"event_name\":\"%s\"".formatted(eventName))) {
                return objectMapper.readTree(line.substring(line.indexOf('{')));
            }
        }

        throw new AssertionError("Audit log not found. eventName=" + eventName);
    }

    @TestConfiguration
    static class InquiryMailSenderTestConfig {

        @Bean
        @Primary
        FakeInquiryMailSender inquiryMailSender() {
            return new FakeInquiryMailSender();
        }
    }

    static class FakeInquiryMailSender implements InquiryMailSender {

        private boolean failNext;
        private String to;
        private String subject;
        private String message;

        @Override
        public void sendReply(String to, String subject, String message) {
            if (failNext) {
                throw new EmailDeliveryException("이메일 발송에 실패했습니다.");
            }

            this.to = to;
            this.subject = subject;
            this.message = message;
        }

        private void failNext() {
            this.failNext = true;
        }

        private void reset() {
            failNext = false;
            to = null;
            subject = null;
            message = null;
        }
    }
}
