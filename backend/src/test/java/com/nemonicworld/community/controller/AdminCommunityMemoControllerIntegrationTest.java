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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.community.service.moderation.CommunityMemoModerationClient;
import com.nemonicworld.support.AbstractReadOnlyIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.support.AppUserTestFixture;
import com.nemonicworld.support.ArtifactGalleryTestFixture;
import com.nemonicworld.support.ArtifactSubtypeTestFixture;
import com.nemonicworld.support.BackofficeAuthTestFixture;
import com.nemonicworld.support.CommunityMemoTestFixture;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
class AdminCommunityMemoControllerIntegrationTest extends AbstractReadOnlyIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_LOGIN_ID = "community-admin";
    private static final String ADMIN_NICKNAME = "Community Admin";
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
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private AdminUserTestFixture adminUserFixture;
    private AppUserTestFixture appUserFixture;
    private ArtifactGalleryTestFixture artifactGalleryFixture;
    private ArtifactSubtypeTestFixture artifactSubtypeFixture;
    private CommunityMemoTestFixture communityMemoFixture;

    @MockitoBean
    private CommunityMemoModerationClient moderationClient;

    @BeforeEach
    void prepareTables() {
        adminUserFixture = new AdminUserTestFixture(jdbcTemplate);
        appUserFixture = new AppUserTestFixture(jdbcTemplate);
        artifactGalleryFixture = new ArtifactGalleryTestFixture(jdbcTemplate);
        artifactSubtypeFixture = new ArtifactSubtypeTestFixture(jdbcTemplate);
        communityMemoFixture = new CommunityMemoTestFixture(jdbcTemplate);
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
    void adminFiltersCommunityMemosByReportedStatus() throws Exception {
        UUID authorUuid = insertAppUser("신고필터");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID reportedMemoId = insertCommunityMemo(authorUuid, null, "reported-original.png", null, false, null, null, 2,
            "allowed", null, null, baseTime, baseTime.plusMinutes(1));
        UUID unreportedMemoId = insertCommunityMemo(authorUuid, null, "unreported-original.png", null, false, null,
            null, 0, "allowed", null, null, baseTime, baseTime.plusMinutes(2));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("reported", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(reportedMemoId.toString()))
            .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("reported", "false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(unreportedMemoId.toString()))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminKeywordSearchTreatsLikeWildcardsAsLiteralText() throws Exception {
        UUID authorUuid = insertAppUser("Percent");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        UUID percentMemoId = insertCommunityMemo(authorUuid, null, "percent-original.png", null, false, null, null, 0,
            "allowed", "memo text with 100% mark", null, baseTime, baseTime.plusMinutes(1));
        UUID underscoreMemoId = insertCommunityMemo(authorUuid, null, "underscore-original.png", null, false, null,
            null, 0, "allowed", "memo text with under_score mark", null, baseTime, baseTime.plusMinutes(2));
        insertCommunityMemo(authorUuid, null, "plain-original.png", null, false, null, null, 0, "allowed",
            "memo text without wildcard mark", null, baseTime, baseTime.plusMinutes(3));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("keyword", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(percentMemoId.toString()))
            .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
            .perform(get("/api/v1/admin/community/memos").header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .param("keyword", "_"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].memoId").value(underscoreMemoId.toString()))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminGetsHiddenMemoDetailAndRejectsDeletedOrInvalidMemoId() throws Exception {
        UUID authorUuid = insertAppUser("상세");
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), 5, "allowed", "ocr", 1L,
            LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
        UUID reporterUuid = insertAppUser("상세신고자");
        insertCommunityMemoReport(memoId, reporterUuid, "abuse_hate", "욕설이 포함되어 있어요.",
            LocalDateTime.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS));
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
            .andExpect(jsonPath("$.data.memoImageUrl").value(THUMBNAIL_PUBLIC_URL))
            .andExpect(jsonPath("$.data.reports.length()").value(1))
            .andExpect(jsonPath("$.data.reports[0].reporterUserUuid").value(reporterUuid.toString()))
            .andExpect(jsonPath("$.data.reports[0].reporterNickname").value("상세신고자"))
            .andExpect(jsonPath("$.data.reports[0].reason").value("abuse_hate"))
            .andExpect(jsonPath("$.data.reports[0].reasonDetail").value("욕설이 포함되어 있어요."));

        mockMvc
            .perform(
                get("/api/v1/admin/community/memos/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}", deletedMemoId).header(HttpHeaders.AUTHORIZATION,
            bearerAccessToken())).andExpect(status().isNotFound());
    }

    @Test
    void adminGetsCommunityMemoReportsWithPaginationAndHiddenMemo() throws Exception {
        UUID authorUuid = insertAppUser("작성자");
        UUID reporterA = insertAppUser("신고자A");
        UUID reporterB = insertAppUser("신고자B");
        UUID reporterC = insertAppUser("신고자C");
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", baseTime.plusMinutes(5), 3, "allowed", "ocr", null, baseTime, baseTime);
        long firstReportId = insertCommunityMemoReport(memoId, reporterA, "inappropriate", baseTime.plusMinutes(1));
        long secondReportId = insertCommunityMemoReport(memoId, reporterB, "spam", baseTime.plusMinutes(2));
        long thirdReportId = insertCommunityMemoReport(memoId, reporterC, "other", "기타 신고 상세", baseTime.plusMinutes(3));
        UUID emptyMemoId = insertCommunityMemo(authorUuid, null, "empty-original.png", null, false, null, null, 0,
            "allowed", null, null, baseTime, baseTime);

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("page", "0").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("관리자 커뮤니티 메모 신고 내역 조회 성공"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].reportId").value(thirdReportId))
            .andExpect(jsonPath("$.data.items[0].memoId").value(memoId.toString()))
            .andExpect(jsonPath("$.data.items[0].reporterUserUuid").value(reporterC.toString()))
            .andExpect(jsonPath("$.data.items[0].reporterNickname").value("신고자C"))
            .andExpect(jsonPath("$.data.items[0].reason").value("other"))
            .andExpect(jsonPath("$.data.items[0].reasonDetail").value("기타 신고 상세"))
            .andExpect(jsonPath("$.data.items[0].createdAt").exists())
            .andExpect(jsonPath("$.data.items[1].reportId").value(secondReportId))
            .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("page", "1").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].reportId").value(firstReportId))
            .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", emptyMemoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void adminFiltersCommunityMemoReportsByReasonAndRejectsInvalidInputs() throws Exception {
        UUID authorUuid = insertAppUser("필터");
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, false, null,
            null, 2, "allowed", null, null, baseTime, baseTime);
        insertCommunityMemoReport(memoId, insertAppUser("신고1"), "inappropriate", baseTime.plusMinutes(1));
        insertCommunityMemoReport(memoId, insertAppUser("신고2"), "spam", baseTime.plusMinutes(2));
        insertCommunityMemoReport(memoId, insertAppUser("신고3"), "inappropriate", baseTime.plusMinutes(3));
        UUID deletedMemoId = insertCommunityMemo(authorUuid, null, "deleted-original.png", null, false, null, null, 0,
            "allowed", null, null, baseTime, baseTime, baseTime.plusMinutes(4));

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("reason", "inappropriate"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.items[0].reason").value("inappropriate"))
            .andExpect(jsonPath("$.data.items[1].reason").value("inappropriate"));

        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("reason", "invalid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("커뮤니티 메모 신고 사유가 올바르지 않습니다."));
        mockMvc.perform(get("/api/v1/admin/community/memos/not-a-uuid/reports").header(HttpHeaders.AUTHORIZATION,
            bearerAccessToken())).andExpect(status().isBadRequest());
        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("page", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("size", "0"))
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).param("size", "101"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", deletedMemoId)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())).andExpect(status().isNotFound());
    }

    @Test
    void adminCommunityMemoReportLookupRequiresAdminAndDoesNotMutateMemo() throws Exception {
        UUID authorUuid = insertAppUser("부작용");
        UUID reporterUuid = insertAppUser("신고자");
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, false, null,
            null, 7, "allowed", "ocr", null, baseTime, baseTime.plusMinutes(1));
        insertCommunityMemoReport(memoId, reporterUuid, "spam", baseTime.plusMinutes(2));

        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId).header(ANONYMOUS_USER_UUID_HEADER,
            reporterUuid.toString())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId).header(HttpHeaders.AUTHORIZATION,
            "Bearer invalid-token")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/community/memos/{memoId}/reports", memoId).header(HttpHeaders.AUTHORIZATION,
            bearerAccessToken())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));

        assertThat(
            jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class, memoId))
            .isEqualTo(7);
        assertThat(
            jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class, memoId))
            .isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isEqualTo(baseTime.plusMinutes(1));
        assertPreservedMemoSnapshot(memoId);
        verifyNoInteractions(moderationClient);
    }

    @Test
    void adminHidesVisibleMemoWithAdminHiddenAndUserApisExcludeIt(CapturedOutput output) throws Exception {
        UUID authorUuid = insertAppUser("숨김");
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        UUID memoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, false, null,
            null, 2, "allowed", "ocr", null, createdAt, createdAt);
        String hideReason = "신고 내용 확인 결과 부적절한 이미지로 판단했습니다.";

        mockMvc
            .perform(patch("/api/v1/admin/community/memos/{memoId}/hide", memoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "community-hide-audit-test").header("X-Forwarded-For", "10.10.30.11, 10.10.30.12")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "reason": "%s"
                    }
                    """.formatted(hideReason)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("커뮤니티 메모 숨김 처리 성공"))
            .andExpect(jsonPath("$.data.isHidden").value(true))
            .andExpect(jsonPath("$.data.hiddenReason").value("admin_hidden"))
            .andExpect(jsonPath("$.data.reviewedBy").value(ADMIN_ID)).andExpect(jsonPath("$.data.reviewedAt").exists());

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
        assertThat(jdbcTemplate.queryForObject("SELECT reviewed_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, memoId)).isNotNull();
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

        JsonNode auditLog = findAuditLog(output, "memo_soft_delete");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("level").asText()).isEqualTo("INFO");
        assertThat(auditLog.path("service").asText()).isEqualTo("backoffice-api");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("community-hide-audit-test");
        assertThat(metadata.path("actor_id").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(metadata.path("actor_role").asText()).isEqualTo("admin");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.30.11");
        assertThat(metadata.path("target_type").asText()).isEqualTo("memo");
        assertThat(metadata.path("target_id").asText()).isEqualTo(memoId.toString());
        assertThat(metadata.path("action").asText()).isEqualTo("delete");
        assertThat(metadata.path("reason").asText()).isEqualTo(hideReason);
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("state_changed").asBoolean()).isTrue();
        assertThat(metadata.path("before").path("is_hidden").asBoolean()).isFalse();
        assertThat(metadata.path("after").path("is_hidden").asBoolean()).isTrue();
        assertThat(metadata.path("after").path("hidden_reason").asText()).isEqualTo("admin_hidden");
        assertThat(auditLog.toString()).doesNotContain(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, ORIGINAL_PUBLIC_URL,
            THUMBNAIL_PUBLIC_URL, "ocr");

        mockMvc.perform(patch("/api/v1/admin/community/memos/{memoId}/hide", memoId)
            .header(HttpHeaders.AUTHORIZATION, bearerAccessToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"   \"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(moderationClient);
    }

    @Test
    void adminRestoresHiddenMemoWithoutImmediateFifoOrDataMutation(CapturedOutput output) throws Exception {
        UUID authorUuid = insertAppUser("복구");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        for (int index = 0; index < 51; index++) {
            insertCommunityMemo(authorUuid, null, "visible-%s.png".formatted(index), null, false, null, null, 0,
                "allowed", null, null, baseTime.plusSeconds(index), baseTime.plusSeconds(index));
        }
        UUID hiddenMemoId = insertCommunityMemo(authorUuid, null, ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, true,
            "report_threshold", baseTime.plusHours(1), 5, "allowed", "ocr", 9L, baseTime, baseTime.plusHours(1));
        String restoreReason = "오신고로 확인되어 복구합니다.";

        mockMvc
            .perform(patch("/api/v1/admin/community/memos/{memoId}/restore", hiddenMemoId)
                .header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
                .header("X-Trace-Id", "community-restore-audit-test").header("X-Real-IP", "10.10.30.21")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "reason": "%s"
                    }
                    """.formatted(restoreReason)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("커뮤니티 메모 숨김 복구 성공"))
            .andExpect(jsonPath("$.data.isHidden").value(false))
            .andExpect(jsonPath("$.data.hiddenReason").value(nullValue()))
            .andExpect(jsonPath("$.data.reportCount").value(0)).andExpect(jsonPath("$.data.reviewedBy").value(ADMIN_ID))
            .andExpect(jsonPath("$.data.reviewedAt").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT is_hidden FROM community_memo WHERE id = ?", Boolean.class,
            hiddenMemoId)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT hidden_reason FROM community_memo WHERE id = ?", String.class,
            hiddenMemoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM community_memo WHERE id = ?", LocalDateTime.class,
            hiddenMemoId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT report_count FROM community_memo WHERE id = ?", Integer.class,
            hiddenMemoId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT reviewed_at FROM community_memo WHERE id = ?",
            LocalDateTime.class, hiddenMemoId)).isNotNull();
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
        JsonNode auditLog = findAuditLog(output, "memo_restore");
        JsonNode metadata = auditLog.path("metadata");
        assertThat(auditLog.path("level").asText()).isEqualTo("INFO");
        assertThat(auditLog.path("service").asText()).isEqualTo("backoffice-api");
        assertThat(auditLog.path("trace_id").asText()).isEqualTo("community-restore-audit-test");
        assertThat(metadata.path("actor_id").asText()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(metadata.path("actor_role").asText()).isEqualTo("admin");
        assertThat(metadata.path("actor_ip").asText()).isEqualTo("10.10.30.21");
        assertThat(metadata.path("target_type").asText()).isEqualTo("memo");
        assertThat(metadata.path("target_id").asText()).isEqualTo(hiddenMemoId.toString());
        assertThat(metadata.path("action").asText()).isEqualTo("restore");
        assertThat(metadata.path("reason").asText()).isEqualTo(restoreReason);
        assertThat(metadata.path("result").asText()).isEqualTo("success");
        assertThat(metadata.path("state_changed").asBoolean()).isTrue();
        assertThat(metadata.path("before").path("is_hidden").asBoolean()).isTrue();
        assertThat(metadata.path("before").path("hidden_reason").asText()).isEqualTo("report_threshold");
        assertThat(metadata.path("after").path("is_hidden").asBoolean()).isFalse();
        assertThat(metadata.path("after").path("hidden_reason").isNull()).isTrue();
        assertThat(auditLog.toString()).doesNotContain(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, ORIGINAL_PUBLIC_URL,
            THUMBNAIL_PUBLIC_URL, "ocr");

        JsonNode restoredAuditLog = findAuditLog(output, "admin_community_memo_restored");
        JsonNode restoredMetadata = restoredAuditLog.path("metadata");
        assertThat(restoredMetadata.path("before_report_count").asInt()).isEqualTo(5);
        assertThat(restoredMetadata.path("after_report_count").asInt()).isZero();

        verifyNoInteractions(moderationClient);
    }

    private void createTables() {
        adminUserFixture.ensureTable();
        appUserFixture.ensureTable();
        artifactGalleryFixture.ensureArtifactTable();
        artifactSubtypeFixture.ensureFlipbookArtifactTable();
        communityMemoFixture.ensureCommunityMemoTables();
    }

    private void cleanTables() {
        communityMemoFixture.deleteCommunityMemoRows();
        artifactGalleryFixture.deleteArtifactRows();
        appUserFixture.deleteAll();
        adminUserFixture.deleteAll();
    }

    private void insertAdminUser() {
        adminUserFixture.insertEncoded(ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME, ADMIN_EMAIL, AdminRole.ADMIN);
    }

    private UUID insertAppUser(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        appUserFixture.insertAnonymous(userUuid, nickname, "MangoApp/1.0", now, now, now);

        return userUuid;
    }

    private long insertCommunityMemoReport(UUID memoId, UUID reporterUuid, String reason, LocalDateTime createdAt) {
        return insertCommunityMemoReport(memoId, reporterUuid, reason, null, createdAt);
    }

    private long insertCommunityMemoReport(UUID memoId, UUID reporterUuid, String reason, String reasonDetail,
        LocalDateTime createdAt) {
        return communityMemoFixture.insertReportWithNextId(memoId, reporterUuid, reason, reasonDetail, createdAt);
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
        communityMemoFixture.insertMemo(memoId, userUuid, artifactId, 120.5, -30.0, 12, 5.5, "{\"scale\":1.0}",
            bodyImageUrl, thumbnailImageUrl, createdAt, reportCount, hidden, hiddenReason, hiddenAt, moderationStatus,
            ocrText, "[\"safe\"]", createdAt, reviewedBy, null, createdAt, updatedAt, deletedAt, null);

        return memoId;
    }

    private JsonNode findAuditLog(CapturedOutput output, String eventName) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            if (line.contains("\"event_name\":\"%s\"".formatted(eventName))) {
                return objectMapper.readTree(line.substring(line.indexOf('{')));
            }
        }

        throw new AssertionError("Audit log not found. eventName=" + eventName);
    }

    private String bearerAccessToken() {
        return BackofficeAuthTestFixture.bearerAccessToken(jwtTokenProvider, ADMIN_ID, ADMIN_LOGIN_ID, ADMIN_NICKNAME,
            ADMIN_EMAIL, AdminRole.ADMIN);
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

}
