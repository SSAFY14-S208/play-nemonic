package com.nemonicworld.gms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.fortune.service.gms.FortuneGmsClient;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.support.AbstractReadOnlyIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.support.BackofficeAuthTestFixture;
import com.nemonicworld.support.GmsPromptTestFixture;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
class GmsPromptControllerIntegrationTest extends AbstractReadOnlyIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_LOGIN_ID = "prompt-admin";
    private static final String ADMIN_NICKNAME = "Prompt Admin";
    private static final String ADMIN_EMAIL = "prompt-admin@example.com";
    private static final long VIEWER_ID = 2L;
    private static final String VIEWER_LOGIN_ID = "prompt-viewer";
    private static final String VIEWER_NICKNAME = "Prompt Viewer";
    private static final String VIEWER_EMAIL = "prompt-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private AdminUserTestFixture adminUserFixture;
    private GmsPromptTestFixture gmsPromptFixture;

    @MockitoBean
    private FortuneGmsClient fortuneGmsClient;

    @BeforeEach
    void prepareTables() {
        reset(fortuneGmsClient);
        adminUserFixture = new AdminUserTestFixture(jdbcTemplate);
        adminUserFixture.ensureTable();
        gmsPromptFixture = new GmsPromptTestFixture(jdbcTemplate);
        gmsPromptFixture.ensurePromptTables();
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
            CREATE TABLE IF NOT EXISTS fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL,
                fortune_image_url VARCHAR(200) NOT NULL,
                user_id UUID NOT NULL,
                fortune_date DATE NOT NULL
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
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM artifact");
        gmsPromptFixture.deletePromptRows();
        adminUserFixture.deleteAll();
        insertAdminUser();
    }

    @Test
    void adminCreatesPrompt(CapturedOutput output) throws Exception {
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "prompt-create-audit-test").header("X-Real-IP", "10.10.50.11")
                .contentType(MediaType.APPLICATION_JSON).content(createRequestBody("Daily fortune")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Daily fortune"))
            .andExpect(jsonPath("$.data.content").value("Prompt body for {{nickname}}."))
            .andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.createdBy").value(ADMIN_ID)).andExpect(jsonPath("$.data.isActive").value(false))
            .andExpect(jsonPath("$.data.status").value("not_active"));

        assertThat(countPromptsByName("Daily fortune")).isEqualTo(1);
        assertThat(countPromptsByFeatureType("fortune")).isEqualTo(1);

        JsonNode auditLog = findAuditLog(output, "prompt_update");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("service").asText()).isEqualTo("backoffice-api");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("prompt-create-audit-test");
        assertThat(metadata.path("actor_id").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(metadata.path("actor_role").asText()).isEqualTo("admin");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.50.11");
        assertThat(metadata.path("target_type").asText()).isEqualTo("prompt");
        assertThat(metadata.path("action").asText()).isEqualTo("create");
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("after").path("name").asText()).isEqualTo("Daily fortune");
        assertThat(metadata.path("after").path("feature_type").asText()).isEqualTo("fortune");
        assertThat(auditLog.toString()).doesNotContain("Prompt body for {{nickname}}.");
    }

    @Test
    void adminPromptCreationRejectsDuplicatedName() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content(createRequestBody("Daily fortune")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminGetsPromptList() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        insertPrompt(11L, "Sticker prompt", "sticker", null);

        mockMvc.perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].id").value(11L))
            .andExpect(jsonPath("$.data.items[0].name").value("Sticker prompt"))
            .andExpect(jsonPath("$.data.items[1].id").value(10L)).andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void viewerGetsPromptListAndCurrentPrompt() throws Exception {
        insertAdminUser(VIEWER_ID, VIEWER_LOGIN_ID, VIEWER_NICKNAME, VIEWER_EMAIL, AdminRole.VIEWER);
        insertPrompt(10L, "Active fortune", "Current prompt body.", "fortune", null, true);

        String viewerToken = bearerAccessToken(VIEWER_ID, VIEWER_LOGIN_ID, VIEWER_NICKNAME, VIEWER_EMAIL,
            AdminRole.VIEWER);
        mockMvc.perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, viewerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(10L));

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/current").header(HttpHeaders.AUTHORIZATION, viewerToken)
                .queryParam("featureType", "fortune"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.source").value("database")).andExpect(jsonPath("$.data.prompt.id").value(10L));
    }

    @Test
    void adminGetsEmptyPromptList() throws Exception {
        mockMvc.perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void adminSearchesPromptListByNameKeyword() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        insertPrompt(11L, "Sticker prompt", "sticker", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "daily"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10L))
            .andExpect(jsonPath("$.data.items[0].name").value("Daily fortune"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminSearchesPromptListByContentKeyword() throws Exception {
        insertPrompt(10L, "Daily fortune", "Use moon phase for fortune.", "fortune", null);
        insertPrompt(11L, "Sticker prompt", "Sticker image generation prompt.", "sticker", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "moon"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10L))
            .andExpect(jsonPath("$.data.items[0].content").value("Use moon phase for fortune."))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void promptKeywordSearchTreatsLikeWildcardsAsLiteralText() throws Exception {
        insertPrompt(10L, "Percent prompt", "Use 100% of the provided context.", "fortune", null);
        insertPrompt(11L, "Underscore prompt", "Use under_score marker.", "sticker", null);
        insertPrompt(12L, "Plain prompt", "Use plain marker.", "fortune", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10L)).andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("keyword", "_"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(11L)).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminFiltersPromptListByFeatureType() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        insertPrompt(11L, "Sticker prompt", "sticker", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("featureType", "sticker"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(11L))
            .andExpect(jsonPath("$.data.items[0].featureType").value("sticker"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminFiltersPromptListByActiveStatus() throws Exception {
        insertPrompt(10L, "Active fortune", "Prompt body.", "fortune", null, true);
        insertPrompt(11L, "Draft fortune", "Prompt body.", "fortune", null, false);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("status", "active"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10L))
            .andExpect(jsonPath("$.data.items[0].isActive").value(true))
            .andExpect(jsonPath("$.data.items[0].status").value("active"));

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("status", "not_active"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(11L))
            .andExpect(jsonPath("$.data.items[0].isActive").value(false))
            .andExpect(jsonPath("$.data.items[0].status").value("not_active"));
    }

    @Test
    void adminGetsCurrentFortunePromptFromActivePrompt() throws Exception {
        insertPrompt(10L, "Active fortune", "Current prompt body.", "fortune", null, true);
        insertPrompt(11L, "Draft fortune", "Draft prompt body.", "fortune", null, false);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/current")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).queryParam("featureType", "fortune"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.source").value("database")).andExpect(jsonPath("$.data.prompt.id").value(10L))
            .andExpect(jsonPath("$.data.prompt.content").value("Current prompt body."))
            .andExpect(jsonPath("$.data.prompt.isActive").value(true));
    }

    @Test
    void adminGetsCurrentFortunePromptFallbackWhenNoActivePromptExists() throws Exception {
        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/current")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).queryParam("featureType", "fortune"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.source").value("default"))
            .andExpect(jsonPath("$.data.prompt.id").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.prompt.isActive").value(true))
            .andExpect(jsonPath("$.data.prompt.content").isNotEmpty());
    }

    @Test
    void adminPromptListExcludesDeletedPrompt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertPrompt(10L, "Daily fortune", "fortune", null);
        insertPrompt(11L, "Deleted prompt", "fortune", deletedAt);

        mockMvc.perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10L)).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminPromptListAppliesPagination() throws Exception {
        insertPrompt(10L, "First prompt", "fortune", null);
        insertPrompt(11L, "Second prompt", "fortune", null);
        insertPrompt(12L, "Third prompt", "sticker", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .queryParam("page", "1").queryParam("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(11L)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1)).andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void adminPromptListRejectsUnauthenticatedRequest() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc.perform(get("/api/v1/backoffice/gms/prompts")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void viewerCannotRunPromptWorkflows() throws Exception {
        insertAdminUser(VIEWER_ID, VIEWER_LOGIN_ID, VIEWER_NICKNAME, VIEWER_EMAIL, AdminRole.VIEWER);
        insertPrompt(10L, "Active fortune", "Saved prompt body.", "fortune", null, true);
        String viewerToken = bearerAccessToken(VIEWER_ID, VIEWER_LOGIN_ID, VIEWER_NICKNAME, VIEWER_EMAIL,
            AdminRole.VIEWER);

        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts").header(HttpHeaders.AUTHORIZATION, viewerToken)
                .contentType(MediaType.APPLICATION_JSON).content(createRequestBody("Viewer create")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview").header(HttpHeaders.AUTHORIZATION, viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("fortune", "Candidate prompt body.", sajuRequestJson())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/{promptId}/test", 10L)
                .header(HttpHeaders.AUTHORIZATION, viewerToken).contentType(MediaType.APPLICATION_JSON)
                .content(promptTestRequestBody(sajuRequestJson())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));
        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, viewerToken).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "name": "Viewer update"
                    }
                    """))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));
        mockMvc
            .perform(
                delete("/api/v1/backoffice/gms/prompts/{promptId}", 10L).header(HttpHeaders.AUTHORIZATION, viewerToken))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/{promptId}/activate", 10L).header(HttpHeaders.AUTHORIZATION,
                viewerToken))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("관리자 작업 권한이 필요합니다."));

        assertThat(countPromptsByName("Viewer create")).isZero();
        assertThat(findPromptName(10L)).isEqualTo("Active fortune");
        assertThat(findPromptIsActive(10L)).isTrue();
        assertThat(findPromptDeletedAt(10L)).isNull();
        verifyNoInteractions(fortuneGmsClient);
    }

    @Test
    void adminPreviewsFortunePromptWithoutPersistingResult(CapturedOutput output) throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        when(fortuneGmsClient.generate(eq("Candidate prompt body."), any(JsonNode.class)))
            .thenReturn(sampleGmsResult());

        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("fortune", "Candidate prompt body.", sajuRequestJson())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.fortune.title").value("Preview title"))
            .andExpect(jsonPath("$.data.fortune.summary").value("Preview summary"))
            .andExpect(jsonPath("$.data.fortune.overallLuck").value(80))
            .andExpect(jsonPath("$.data.fortune.loveLuck").value(70))
            .andExpect(jsonPath("$.data.fortune.workLuck").value(65))
            .andExpect(jsonPath("$.data.fortune.moneyLuck").value(90))
            .andExpect(jsonPath("$.data.fortune.luckyColor").value("Blue"))
            .andExpect(jsonPath("$.data.fortune.luckyColorHex").exists())
            .andExpect(jsonPath("$.data.fortune.luckyKeyword").value("Focus"))
            .andExpect(jsonPath("$.data.fortune.luckyDirection").value("East"))
            .andExpect(jsonPath("$.data.fortune.caution").value("Move slowly"))
            .andExpect(jsonPath("$.data.fortune.postitLine").value("Stay calm today"))
            .andExpect(jsonPath("$.data.saju.calendarType").value("solar"))
            .andExpect(jsonPath("$.data.saju.yearPillar").value("gapja"))
            .andExpect(jsonPath("$.data.saju.hourPillar").value("gengo"))
            .andExpect(jsonPath("$.data.design.cardTheme").value("default"))
            .andExpect(jsonPath("$.data.design.bgColor").value("#F5F1E8"))
            .andExpect(jsonPath("$.data.design.accentColor").value("#506996"))
            .andExpect(jsonPath("$.data.design.iconKey").value("sun"))
            .andExpect(jsonPath("$.data.previewImageBase64", startsWith("data:image/png;base64,")));

        assertThat(countRows("gms_prompt_template")).isEqualTo(1);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("fortune_artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(output).doesNotContain("Candidate prompt body.", "Preview title", "Preview summary");
        verify(fortuneGmsClient).generate(eq("Candidate prompt body."), any(JsonNode.class));
    }

    @Test
    void adminTestsSavedFortunePromptWithoutPersistingResult() throws Exception {
        insertPrompt(10L, "Daily fortune", "Saved prompt body.", "fortune", null, false);
        when(fortuneGmsClient.generate(eq("Saved prompt body."), any(JsonNode.class))).thenReturn(sampleGmsResult());

        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/{promptId}/test", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(promptTestRequestBody(sajuRequestJson())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.fortune.title").value("Preview title"))
            .andExpect(jsonPath("$.data.previewImageBase64", startsWith("data:image/png;base64,")));

        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("fortune_artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        verify(fortuneGmsClient).generate(eq("Saved prompt body."), any(JsonNode.class));
    }

    @Test
    void adminPromptPreviewRejectsInvalidSampleSaju() throws Exception {
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("fortune", "Candidate prompt body.", """
                    {
                      "calendarType": "solar",
                      "yearPillar": "gapja"
                    }
                    """)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("만세력 결과 정보가 올바르지 않습니다."));

        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("fortune_artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        verifyNoInteractions(fortuneGmsClient);
    }

    @Test
    void adminPromptPreviewRejectsBlankContent() throws Exception {
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("fortune", " ", sajuRequestJson())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효성 검사 실패"))
            .andExpect(jsonPath("$.errors.content").value("프롬프트 본문을 입력해야 합니다."));

        verifyNoInteractions(fortuneGmsClient);
    }

    @Test
    void adminPromptPreviewRejectsUnsupportedFeatureType() throws Exception {
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview")
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("sticker", "Candidate prompt body.", sajuRequestJson())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("지원하지 않는 GMS 프롬프트 미리보기 타입입니다."));

        verifyNoInteractions(fortuneGmsClient);
    }

    @Test
    void adminPromptPreviewRejectsUnauthenticatedRequest() throws Exception {
        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/preview").contentType(MediaType.APPLICATION_JSON)
                .content(previewRequestBody("fortune", "Candidate prompt body.", sajuRequestJson())))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(fortuneGmsClient);
    }

    @Test
    void adminGetsPromptDetail() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/{promptId}", 10L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10L)).andExpect(jsonPath("$.data.name").value("Daily fortune"))
            .andExpect(jsonPath("$.data.content").value("Prompt body for {{nickname}}."))
            .andExpect(jsonPath("$.data.featureType").value("fortune"))
            .andExpect(jsonPath("$.data.createdBy").value(ADMIN_ID))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty()).andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    void adminPromptDetailRejectsUnknownId() throws Exception {
        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/{promptId}", 999L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminPromptDetailRejectsAlreadyDeletedPrompt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertPrompt(10L, "Daily fortune", "fortune", deletedAt);

        mockMvc
            .perform(get("/api/v1/backoffice/gms/prompts/{promptId}", 10L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminPromptDetailRejectsUnauthenticatedRequest() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc.perform(get("/api/v1/backoffice/gms/prompts/{promptId}", 10L)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminDeletesPrompt(CapturedOutput output) throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(delete("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).header("X-Trace-Id", "prompt-delete-audit-test")
                .header("X-Forwarded-For", "10.10.50.21, 10.10.50.22"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(countPromptsByName("Daily fortune")).isEqualTo(1);
        assertThat(countActivePromptsById(10L)).isZero();
        assertThat(findPromptDeletedAt(10L)).isNotNull();

        JsonNode auditLog = findAuditLog(output, "prompt_update");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("prompt-delete-audit-test");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.50.21");
        assertThat(metadata.path("target_type").asText()).isEqualTo("prompt");
        assertThat(metadata.path("target_id").asText()).isEqualTo("10");
        assertThat(metadata.path("action").asText()).isEqualTo("delete");
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("before").path("name").asText()).isEqualTo("Daily fortune");
        assertThat(metadata.path("before").path("feature_type").asText()).isEqualTo("fortune");
        assertThat(metadata.path("after").path("deleted").asBoolean()).isTrue();
        assertThat(auditLog.toString()).doesNotContain("Prompt body for {{nickname}}.");
    }

    @Test
    void adminPromptDeletionRejectsUnknownId() throws Exception {
        mockMvc
            .perform(delete("/api/v1/backoffice/gms/prompts/{promptId}", 999L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminPromptDeletionRejectsAlreadyDeletedPrompt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertPrompt(10L, "Daily fortune", "fortune", deletedAt);

        mockMvc
            .perform(delete("/api/v1/backoffice/gms/prompts/{promptId}", 10L).header(HttpHeaders.AUTHORIZATION,
                bearerAccessToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(findPromptDeletedAt(10L)).isEqualTo(deletedAt);
    }

    @Test
    void adminActivatesPromptAndDeactivatesPreviousPrompt(CapturedOutput output) throws Exception {
        insertPrompt(10L, "Active fortune", "Current prompt body.", "fortune", null, true);
        insertPrompt(11L, "Next fortune", "Next prompt body.", "fortune", null, false);

        mockMvc
            .perform(post("/api/v1/backoffice/gms/prompts/{promptId}/activate", 11L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).header("X-Trace-Id", "prompt-activate-test")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(11L)).andExpect(jsonPath("$.data.isActive").value(true))
            .andExpect(jsonPath("$.data.status").value("active"))
            .andExpect(jsonPath("$.data.activatedBy").value(ADMIN_ID));

        assertThat(findPromptIsActive(10L)).isFalse();
        assertThat(findPromptIsActive(11L)).isTrue();
        assertThat(findFeatureStateCurrentPromptId("fortune")).isEqualTo(11L);

        JsonNode auditLog = findAuditLog(output, "prompt_update");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("prompt-activate-test");
        assertThat(metadata.path("action").asText()).isEqualTo("activate");
        assertThat(metadata.path("before").path("id").asText()).isEqualTo("10");
        assertThat(metadata.path("after").path("id").asText()).isEqualTo("11");
    }

    @Test
    void adminPromptActivationRejectsAlreadyDeletedPrompt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertPrompt(10L, "Daily fortune", "fortune", deletedAt);

        mockMvc.perform(post("/api/v1/backoffice/gms/prompts/{promptId}/activate", 10L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminUpdatesPrompt(CapturedOutput output) throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        LocalDateTime beforeUpdatedAt = findPromptUpdatedAt(10L);

        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).header("X-Trace-Id", "prompt-update-audit-test")
                .header("X-Real-IP", "10.10.50.31").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "name": "Updated fortune",
                      "content": "Updated prompt body.",
                      "featureType": "sticker"
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10L)).andExpect(jsonPath("$.data.name").value("Updated fortune"))
            .andExpect(jsonPath("$.data.content").value("Updated prompt body."))
            .andExpect(jsonPath("$.data.featureType").value("sticker"));

        assertThat(findPromptName(10L)).isEqualTo("Updated fortune");
        assertThat(findPromptContent(10L)).isEqualTo("Updated prompt body.");
        assertThat(findPromptFeatureType(10L)).isEqualTo("sticker");
        assertThat(findPromptUpdatedAt(10L)).isAfter(beforeUpdatedAt);

        JsonNode auditLog = findAuditLog(output, "prompt_update");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("prompt-update-audit-test");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.50.31");
        assertThat(metadata.path("target_type").asText()).isEqualTo("prompt");
        assertThat(metadata.path("target_id").asText()).isEqualTo("10");
        assertThat(metadata.path("action").asText()).isEqualTo("update");
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("before").path("name").asText()).isEqualTo("Daily fortune");
        assertThat(metadata.path("before").path("feature_type").asText()).isEqualTo("fortune");
        assertThat(metadata.path("after").path("name").asText()).isEqualTo("Updated fortune");
        assertThat(metadata.path("after").path("feature_type").asText()).isEqualTo("sticker");
        assertThat(metadata.path("after").path("content_changed").asBoolean()).isTrue();
        assertThat(auditLog.toString()).doesNotContain("Prompt body for {{nickname}}.", "Updated prompt body.");
    }

    @Test
    void adminUpdatesPromptNameOnly() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Renamed fortune"
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Renamed fortune"))
            .andExpect(jsonPath("$.data.content").value("Prompt body for {{nickname}}."))
            .andExpect(jsonPath("$.data.featureType").value("fortune"));
    }

    @Test
    void adminUpdatesPromptContentOnly() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "Content only update."
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Daily fortune"))
            .andExpect(jsonPath("$.data.content").value("Content only update."))
            .andExpect(jsonPath("$.data.featureType").value("fortune"));
    }

    @Test
    void adminUpdatesPromptFeatureTypeOnly() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "featureType": "sticker"
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Daily fortune"))
            .andExpect(jsonPath("$.data.content").value("Prompt body for {{nickname}}."))
            .andExpect(jsonPath("$.data.featureType").value("sticker"));
    }

    @Test
    void adminPromptUpdateRejectsUnknownId() throws Exception {
        mockMvc.perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 999L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "name": "Updated fortune"
                }
                """)).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminPromptUpdateRejectsAlreadyDeletedPrompt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        insertPrompt(10L, "Daily fortune", "fortune", deletedAt);

        mockMvc.perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "name": "Updated fortune"
                }
                """)).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(findPromptName(10L)).isEqualTo("Daily fortune");
        assertThat(findPromptDeletedAt(10L)).isEqualTo(deletedAt);
    }

    @Test
    void adminPromptUpdateRejectsEmptyBody() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc
            .perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void adminPromptUpdateRejectsDuplicatedName() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);
        insertPrompt(11L, "Sticker prompt", "sticker", null);

        mockMvc.perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "name": "Sticker prompt"
                }
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(findPromptName(10L)).isEqualTo("Daily fortune");
    }

    @Test
    void adminPromptUpdateAllowsOwnName() throws Exception {
        insertPrompt(10L, "Daily fortune", "fortune", null);

        mockMvc.perform(patch("/api/v1/backoffice/gms/prompts/{promptId}", 10L)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "name": "Daily fortune"
                }
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Daily fortune"));

        assertThat(countPromptsByName("Daily fortune")).isEqualTo(1);
    }

    private void insertAdminUser() {
        insertAdminUser(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME, ADMIN_EMAIL, AdminRole.ADMIN);
    }

    private void insertAdminUser(long id, String loginId, String nickname, String email, AdminRole role) {
        adminUserFixture.insertEncoded(id, loginId, nickname, email, role);
    }

    private void insertPrompt(long id, String name, String featureType, LocalDateTime deletedAt) {
        insertPrompt(id, name, "Prompt body for {{nickname}}.", featureType, deletedAt);
    }

    private void insertPrompt(long id, String name, String content, String featureType, LocalDateTime deletedAt) {
        insertPrompt(id, name, content, featureType, deletedAt, false);
    }

    private void insertPrompt(long id, String name, String content, String featureType, LocalDateTime deletedAt,
        boolean active) {
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        if (active) {
            gmsPromptFixture.insertActivePromptTemplate(id, name, content, featureType, ADMIN_ID, now, deletedAt,
                ADMIN_ID);
            return;
        }

        gmsPromptFixture.insertPromptTemplate(id, name, content, featureType, ADMIN_ID, now, deletedAt);
    }

    private String createRequestBody(String name) {
        return """
            {
              "name": "%s",
              "content": "Prompt body for {{nickname}}.",
              "featureType": "fortune"
            }
            """.formatted(name);
    }

    private String previewRequestBody(String featureType, String content, String sampleSajuJson) {
        return """
            {
              "featureType": "%s",
              "content": "%s",
              "sampleSaju": %s
            }
            """.formatted(featureType, content, sampleSajuJson);
    }

    private String promptTestRequestBody(String sampleSajuJson) {
        return """
            {
              "sampleSaju": %s
            }
            """.formatted(sampleSajuJson);
    }

    private String sajuRequestJson() {
        return """
            {
              "calendarType": "solar",
              "yearPillar": "gapja",
              "monthPillar": "byeongin",
              "dayPillar": "mujin",
              "hourPillar": "gengo",
              "dayMasterElement": "wood",
              "dayBranchElement": "earth",
              "dayMasterYinYang": "yang",
              "dayBranchYinYang": "yang"
            }
            """;
    }

    private FortuneGmsResult sampleGmsResult() {
        return new FortuneGmsResult("Preview title", "Preview summary", 80, 70, 65, 90, "Blue", "Focus", "East",
            "Move slowly", "Stay calm today", "default", "#F5F1E8", "#506996", "sun");
    }

    private String bearerAccessToken() {
        return bearerAccessToken(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME, ADMIN_EMAIL, AdminRole.ADMIN);
    }

    private String bearerAccessToken(long id, String loginId, String nickname, String email, AdminRole role) {
        return BackofficeAuthTestFixture.bearerAccessToken(jwtTokenProvider, id, loginId, nickname, email, role);
    }

    private int countPromptsByName(String name) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gms_prompt_template WHERE prompt_name = ?",
            Integer.class, name);

        return count == null ? 0 : count;
    }

    private int countActivePromptsById(long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gms_prompt_template WHERE id = ? AND deleted_at IS NULL", Integer.class, id);

        return count == null ? 0 : count;
    }

    private LocalDateTime findPromptDeletedAt(long id) {
        Timestamp deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM gms_prompt_template WHERE id = ?",
            Timestamp.class, id);

        return deletedAt == null ? null : deletedAt.toLocalDateTime();
    }

    private LocalDateTime findPromptUpdatedAt(long id) {
        Timestamp updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM gms_prompt_template WHERE id = ?",
            Timestamp.class, id);

        return updatedAt == null ? null : updatedAt.toLocalDateTime();
    }

    private String findPromptName(long id) {
        return jdbcTemplate.queryForObject("SELECT prompt_name FROM gms_prompt_template WHERE id = ?", String.class,
            id);
    }

    private String findPromptContent(long id) {
        return jdbcTemplate.queryForObject("SELECT template_text FROM gms_prompt_template WHERE id = ?", String.class,
            id);
    }

    private String findPromptFeatureType(long id) {
        return jdbcTemplate.queryForObject("SELECT feature_type FROM gms_prompt_template WHERE id = ?", String.class,
            id);
    }

    private boolean findPromptIsActive(long id) {
        Boolean active = jdbcTemplate.queryForObject("SELECT is_active FROM gms_prompt_template WHERE id = ?",
            Boolean.class, id);

        return Boolean.TRUE.equals(active);
    }

    private Long findFeatureStateCurrentPromptId(String featureType) {
        return jdbcTemplate.queryForObject(
            "SELECT current_prompt_id FROM gms_prompt_feature_state WHERE feature_type = ?", Long.class, featureType);
    }

    private int countPromptsByFeatureType(String featureType) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gms_prompt_template WHERE feature_type = ?",
            Integer.class, featureType);

        return count == null ? 0 : count;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);

        return count == null ? 0 : count;
    }

    private JsonNode findAuditLog(CapturedOutput output, String eventName) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            if (line.contains("\"event_name\":\"%s\"".formatted(eventName))) {
                return objectMapper.readTree(line.substring(line.indexOf('{')));
            }
        }

        throw new AssertionError("Audit log not found. eventName=" + eventName);
    }

}
