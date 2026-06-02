package com.nemonicworld.fortune.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.fortune.service.gms.FortuneGmsClient;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
import com.nemonicworld.support.AbstractIntegrationTest;
import com.nemonicworld.support.ArtifactGalleryTestFixture;
import com.nemonicworld.support.ArtifactSubtypeTestFixture;
import com.nemonicworld.support.GmsPromptTestFixture;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
/**
 * 오늘의 운세 생성 가능 여부 조회 API를 통합 검증합니다.
 */
class FortuneControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FortuneGmsClient fortuneGmsClient;

    @MockitoBean
    private FortuneCardStorage fortuneCardStorage;

    private ArtifactGalleryTestFixture artifactGalleryFixture;
    private ArtifactSubtypeTestFixture artifactSubtypeFixture;
    private GmsPromptTestFixture gmsPromptFixture;

    @BeforeEach
    void prepareFortuneTables() {
        reset(fortuneGmsClient, fortuneCardStorage);
        artifactGalleryFixture = new ArtifactGalleryTestFixture(jdbcTemplate);
        artifactSubtypeFixture = new ArtifactSubtypeTestFixture(jdbcTemplate);
        gmsPromptFixture = new GmsPromptTestFixture(jdbcTemplate);
        jdbcTemplate.execute("DROP TABLE IF EXISTS fortune_artifact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS gallery");
        jdbcTemplate.execute("DROP TABLE IF EXISTS artifact");
        gmsPromptFixture.dropPromptTables();
        artifactGalleryFixture.ensureArtifactTable();
        artifactSubtypeFixture.ensureFortuneArtifactTable();
        artifactSubtypeFixture.ensureFortuneUserDateUniqueIndex();
        artifactGalleryFixture.ensureGalleryTable();
        gmsPromptFixture.ensurePromptTables();
    }

    /**
     * 만세력 결과로 운세를 생성하면 artifact, fortune_artifact, gallery가 함께 저장됩니다.
     */
    @Test
    void createFortuneCreatesArtifactAndGallery(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        insertPrompt();
        AtomicBoolean transactionActiveDuringGms = new AtomicBoolean(true);
        AtomicBoolean transactionActiveDuringUpload = captureTransactionActiveDuringFortuneCardUpload();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenAnswer(invocation -> {
            transactionActiveDuringGms.set(TransactionSynchronizationManager.isActualTransactionActive());
            return sampleGmsResult();
        });

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("오늘의 운세 생성 성공")).andExpect(jsonPath("$.data.fortuneId").isNotEmpty())
            .andExpect(jsonPath("$.data.date").value(LocalDate.now(KST_ZONE).toString()))
            .andExpect(jsonPath("$.data.fortune.title").value("오늘은 흐름을 정리하는 날"))
            .andExpect(jsonPath("$.data.fortune.summary").value("차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다."))
            .andExpect(jsonPath("$.data.fortune.overallLuck").value(78))
            .andExpect(jsonPath("$.data.fortune.loveLuck").value(66))
            .andExpect(jsonPath("$.data.fortune.workLuck").value(84))
            .andExpect(jsonPath("$.data.fortune.moneyLuck").value(71))
            .andExpect(jsonPath("$.data.fortune.luckyColor").value("은회색"))
            .andExpect(jsonPath("$.data.fortune.luckyColorHex").value("#C0C0C0"))
            .andExpect(jsonPath("$.data.fortune.luckyKeyword").value("정리"))
            .andExpect(jsonPath("$.data.fortune.luckyDirection").value("동쪽"))
            .andExpect(jsonPath("$.data.fortune.caution").value("결정은 한 템포 늦추는 것이 좋습니다."))
            .andExpect(jsonPath("$.data.fortune.postitLine").value("오늘은 정리할수록 운이 열린다"))
            .andExpect(jsonPath("$.data.saju.calendarType").value("solar"))
            .andExpect(jsonPath("$.data.saju.yearPillar").value("임신"))
            .andExpect(jsonPath("$.data.saju.hourPillar").value("을묘"))
            .andExpect(jsonPath("$.data.design.cardTheme").value("moon"))
            .andExpect(jsonPath("$.data.design.bgColor").value("#2C2C4A"))
            .andExpect(jsonPath("$.data.design.accentColor").value("#C0C0C0"))
            .andExpect(jsonPath("$.data.design.iconKey").value("moon_waning"));

        String savedDescription = jdbcTemplate
            .queryForObject("SELECT description FROM fortune_artifact WHERE user_id = ?", String.class, userUuid);
        Integer artifactCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artifact WHERE kind = 'fortune'",
            Integer.class);
        Integer galleryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gallery WHERE user_id = ?",
            Integer.class, userUuid);

        org.assertj.core.api.Assertions.assertThat(artifactCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(galleryCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(savedDescription).contains("\"title\":\"오늘은 흐름을 정리하는 날\"")
            .contains("\"yearPillar\":\"임신\"").contains("\"luckyColorHex\":\"#C0C0C0\"");
        org.assertj.core.api.Assertions.assertThat(transactionActiveDuringGms.get()).isFalse();
        org.assertj.core.api.Assertions.assertThat(transactionActiveDuringUpload.get()).isFalse();
        verify(fortuneCardStorage).upload(org.mockito.ArgumentMatchers.startsWith("fortune/cards/"), any(byte[].class),
            eq("image/png"));
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_create_requested\"")
            .contains("\"event_name\":\"fortune_gms_succeeded\"").contains("\"event_name\":\"fortune_created\"")
            .contains("\"content_type\":\"fortune\"").contains("\"prompt_version\":\"1\"")
            .doesNotContain("template_text");
    }

    /**
     * 하루 1회 제한 정책상 재조회는 다시 생성하지 않고 최초 생성 응답과 같은 data를 반환합니다.
     */
    @Test
    void getTodayFortuneReturnsSameDataAsCreatedFortune(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        insertPrompt();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenReturn(sampleGmsResult());

        MvcResult createResult = mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isOk()).andReturn();

        MvcResult requeryResult = mockMvc
            .perform(get("/api/v1/fortune/today").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andReturn();

        JsonNode createData = responseData(createResult);
        JsonNode requeryData = responseData(requeryResult);

        org.assertj.core.api.Assertions.assertThat(requeryData).isEqualTo(createData);
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_reissued\"");
        verify(fortuneGmsClient).generate(anyString(), any(JsonNode.class));
        verify(fortuneCardStorage).upload(org.mockito.ArgumentMatchers.startsWith("fortune/cards/"), any(byte[].class),
            eq("image/png"));
    }

    /**
     * 같은 UUID가 같은 KST 날짜에 다시 생성하면 GMS 호출 전 409로 차단합니다.
     */
    @Test
    void createFortuneReturnsConflictWhenUserAlreadyCreatedToday(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        insertFortuneArtifact(userUuid, LocalDate.now(KST_ZONE), LocalDateTime.now().minusMinutes(10));

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("오늘의 운세는 이미 생성했습니다. 내일 다시 이용해주세요."));

        verifyNoInteractions(fortuneGmsClient, fortuneCardStorage);
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_create_requested\"")
            .contains("\"event_name\":\"fortune_daily_limit_blocked\"").contains("\"result\":\"blocked\"");
    }

    /**
     * 만세력 필수 필드가 없으면 GMS 호출 전 400으로 차단합니다.
     */
    @Test
    void createFortuneReturnsBadRequestWhenSajuIsInvalid() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "calendarType": "solar",
                      "yearPillar": "임신"
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("만세력 결과 정보가 올바르지 않습니다."));

        verifyNoInteractions(fortuneGmsClient, fortuneCardStorage);
    }

    /**
     * 태어난 시간을 모르는 사용자는 시주 없이도 운세를 생성할 수 있습니다.
     */
    @Test
    void createFortuneAllowsMissingHourPillar() throws Exception {
        UUID userUuid = createExistingUser();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenReturn(sampleGmsResult());

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBodyWithoutHourPillar()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("오늘의 운세 생성 성공"))
            .andExpect(jsonPath("$.data.saju.hourPillar").value(nullValue()));

        String savedDescription = jdbcTemplate
            .queryForObject("SELECT description FROM fortune_artifact WHERE user_id = ?", String.class, userUuid);

        org.assertj.core.api.Assertions.assertThat(savedDescription).contains("\"hourPillar\":null");
        verify(fortuneGmsClient).generate(anyString(), any(JsonNode.class));
        verify(fortuneCardStorage).upload(org.mockito.ArgumentMatchers.startsWith("fortune/cards/"), any(byte[].class),
            eq("image/png"));
    }

    /**
     * 카드 에셋 메타데이터가 아직 없으면 디자인 필드는 null로 내려도 운세 생성을 허용합니다.
     */
    @Test
    void createFortuneAllowsMissingDesignMetadata() throws Exception {
        UUID userUuid = createExistingUser();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenReturn(sampleGmsResultWithoutDesign());

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.design.cardTheme").value(nullValue()))
            .andExpect(jsonPath("$.data.design.bgColor").value(nullValue()))
            .andExpect(jsonPath("$.data.design.accentColor").value(nullValue()))
            .andExpect(jsonPath("$.data.design.iconKey").value(nullValue()));

        verify(fortuneCardStorage).upload(org.mockito.ArgumentMatchers.startsWith("fortune/cards/"), any(byte[].class),
            eq("image/png"));
    }

    /**
     * GMS가 표시 길이를 넘는 주의 문장을 반환하면 잘라 저장하지 않고 재시도합니다.
     */
    @Test
    void createFortuneRetriesWhenCautionIsTooLong() throws Exception {
        UUID userUuid = createExistingUser();
        insertPrompt();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenReturn(sampleGmsResultWithLongCaution(),
            sampleGmsResult());

        MvcResult result = mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fortune.luckyColorHex").value("#C0C0C0"))
            .andReturn();

        String caution = responseData(result).path("fortune").path("caution").asText();
        String savedDescription = jdbcTemplate
            .queryForObject("SELECT description FROM fortune_artifact WHERE user_id = ?", String.class, userUuid);

        org.assertj.core.api.Assertions.assertThat(caution).hasSizeLessThanOrEqualTo(32).doesNotContain("...");
        org.assertj.core.api.Assertions.assertThat(savedDescription).contains("\"caution\":\"%s\"".formatted(caution));
        verify(fortuneGmsClient, times(2)).generate(anyString(), any(JsonNode.class));
    }

    /**
     * GMS 생성이 최종 실패하면 DB 저장 없이 503을 반환합니다.
     */
    @Test
    void createFortuneDoesNotConsumeDailyLimitWhenGmsFails(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        insertPrompt();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenThrow(new RuntimeException("gms down"));

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요."));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fortune_artifact", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_gms_retried\"")
            .contains("\"event_name\":\"fortune_gms_failed\"").contains("\"retry_count\":2")
            .doesNotContain("fortune_gms_retry").doesNotContain("fortune_gms_final_fail");
        verify(fortuneGmsClient, times(3)).generate(anyString(), any(JsonNode.class));
        verifyNoInteractions(fortuneCardStorage);
    }

    /**
     * GMS가 형식이 맞지 않는 결과를 반환하면 클라이언트 오류가 아니라 GMS 실패로 재시도 후 503을 반환합니다.
     */
    @Test
    void createFortuneRetriesAndReturnsServiceUnavailableWhenGmsResultIsInvalid(CapturedOutput output)
        throws Exception {
        UUID userUuid = createExistingUser();
        insertPrompt();
        when(fortuneGmsClient.generate(anyString(), any(JsonNode.class))).thenReturn(invalidGmsResult());

        mockMvc
            .perform(post("/api/v1/fortune").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType(MediaType.APPLICATION_JSON).content(sajuRequestBody()))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요."));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fortune_artifact", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_gms_retried\"")
            .contains("\"event_name\":\"fortune_gms_failed\"").contains("\"retry_count\":2");
        verify(fortuneGmsClient, times(3)).generate(anyString(), any(JsonNode.class));
        verifyNoInteractions(fortuneCardStorage);
    }

    /**
     * 오늘 생성된 운세가 없으면 생성 가능 상태를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsAvailableWhenUserHasNoFortuneToday(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("오늘의 운세 생성 가능 여부 조회 성공"))
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.fortuneDate").value(today.toString()))
            .andExpect(jsonPath("$.data.todayFortuneId").value(nullValue()))
            .andExpect(jsonPath("$.data.createdAt").value(nullValue()))
            .andExpect(jsonPath("$.data.nextAvailableAt").value(nextAvailableAt(today)));

        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_availability_checked\"")
            .contains("\"available\":true").contains("\"result\":\"success\"");
    }

    /**
     * 오늘 생성된 운세가 있으면 재생성 불가와 기존 fortuneId를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsUnavailableWhenUserAlreadyHasFortuneToday(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10).withSecond(1).truncatedTo(ChronoUnit.SECONDS);
        UUID fortuneId = insertFortuneArtifact(userUuid, today, createdAt);

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.available").value(false))
            .andExpect(jsonPath("$.data.fortuneDate").value(today.toString()))
            .andExpect(jsonPath("$.data.todayFortuneId").value(fortuneId.toString()))
            .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.data.nextAvailableAt").value(nextAvailableAt(today)));

        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_availability_checked\"")
            .contains("\"available\":false").contains(fortuneId.toString());
    }

    /**
     * 다른 사용자가 오늘 운세를 생성했더라도 현재 사용자의 생성 가능 여부에는 영향을 주지 않습니다.
     */
    @Test
    void getTodayAvailabilityIgnoresOtherUsersFortune() throws Exception {
        UUID userUuid = createExistingUser();
        UUID otherUserUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);

        insertFortuneArtifact(otherUserUuid, today, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.todayFortuneId").value(nullValue()));
    }

    /**
     * 오늘 생성된 운세가 있으면 저장된 description JSON을 생성 응답 형식으로 복원해 반환합니다.
     */
    @Test
    void getTodayFortuneReturnsStoredFortuneResult(CapturedOutput output) throws Exception {
        UUID userUuid = createExistingUser();
        LocalDate today = LocalDate.now(KST_ZONE);
        UUID fortuneId = insertFortuneArtifact(userUuid, today, LocalDateTime.now().minusMinutes(10),
            storedFortuneDescription());
        AtomicBoolean transactionActiveDuringUpload = captureTransactionActiveDuringFortuneCardUpload();

        mockMvc.perform(get("/api/v1/fortune/today").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("오늘의 운세 조회 성공"))
            .andExpect(jsonPath("$.data.fortuneId").value(fortuneId.toString()))
            .andExpect(jsonPath("$.data.date").value(today.toString()))
            .andExpect(jsonPath("$.data.fortune.title").value("오늘은 흐름을 정리하는 날"))
            .andExpect(jsonPath("$.data.fortune.summary").value("차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다."))
            .andExpect(jsonPath("$.data.fortune.overallLuck").value(78))
            .andExpect(jsonPath("$.data.fortune.loveLuck").value(66))
            .andExpect(jsonPath("$.data.fortune.workLuck").value(84))
            .andExpect(jsonPath("$.data.fortune.moneyLuck").value(71))
            .andExpect(jsonPath("$.data.fortune.luckyColor").value("은회색"))
            .andExpect(jsonPath("$.data.fortune.luckyColorHex").value("#C0C0C0"))
            .andExpect(jsonPath("$.data.fortune.luckyKeyword").value("정리"))
            .andExpect(jsonPath("$.data.fortune.luckyDirection").value("동쪽"))
            .andExpect(jsonPath("$.data.fortune.caution").value("결정은 한 템포 늦추는 것이 좋습니다."))
            .andExpect(jsonPath("$.data.fortune.postitLine").value("오늘은 정리할수록 운이 열린다"))
            .andExpect(jsonPath("$.data.saju.calendarType").value("solar"))
            .andExpect(jsonPath("$.data.saju.yearPillar").value("임신"))
            .andExpect(jsonPath("$.data.saju.hourPillar").value(nullValue()))
            .andExpect(jsonPath("$.data.design.cardTheme").value("moon"))
            .andExpect(jsonPath("$.data.design.bgColor").value("#2C2C4A"))
            .andExpect(jsonPath("$.data.design.accentColor").value("#C0C0C0"))
            .andExpect(jsonPath("$.data.design.iconKey").value("moon_waning"));

        verifyNoInteractions(fortuneGmsClient);
        verify(fortuneCardStorage).upload(org.mockito.ArgumentMatchers.endsWith("/card-template-v1.png"),
            any(byte[].class), eq("image/png"));
        org.assertj.core.api.Assertions.assertThat(transactionActiveDuringUpload.get()).isFalse();
        String updatedFortuneImageUrl = jdbcTemplate.queryForObject(
            "SELECT fortune_image_url FROM fortune_artifact WHERE artifact_id = ?", String.class, fortuneId);
        String updatedThumbnailUrl = jdbcTemplate.queryForObject("SELECT thumbnail_url FROM artifact WHERE id = ?",
            String.class, fortuneId);
        org.assertj.core.api.Assertions.assertThat(updatedFortuneImageUrl).endsWith("/card-template-v1.png");
        org.assertj.core.api.Assertions.assertThat(updatedThumbnailUrl).isEqualTo(updatedFortuneImageUrl);
        org.assertj.core.api.Assertions.assertThat(output).contains("\"event_name\":\"fortune_reissued\"")
            .contains(fortuneId.toString());
    }

    /**
     * 오늘 생성된 운세가 없으면 재조회 API는 404를 반환합니다.
     */
    @Test
    void getTodayFortuneReturnsNotFoundWhenUserHasNoFortuneToday() throws Exception {
        UUID userUuid = createExistingUser();

        mockMvc.perform(get("/api/v1/fortune/today").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("오늘 생성된 운세를 찾을 수 없습니다."));

        verifyNoInteractions(fortuneGmsClient, fortuneCardStorage);
    }

    /**
     * UUID 헤더가 없거나 형식이 올바르지 않으면 400을 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsBadRequestWhenUuidIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/fortune/today/availability")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc.perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));
    }

    /**
     * UUID 형식은 맞지만 서버에 존재하지 않는 사용자이면 404를 반환합니다.
     */
    @Test
    void getTodayAvailabilityReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        mockMvc
            .perform(get("/api/v1/fortune/today/availability").header(ANONYMOUS_USER_UUID_HEADER,
                UUID.randomUUID().toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
    }

    private UUID createExistingUser() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private AtomicBoolean captureTransactionActiveDuringFortuneCardUpload() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(fortuneCardStorage).upload(anyString(), any(byte[].class), anyString());

        return transactionActive;
    }

    private UUID insertFortuneArtifact(UUID userUuid, LocalDate fortuneDate, LocalDateTime createdAt) {
        return insertFortuneArtifact(userUuid, fortuneDate, createdAt, "{}");
    }

    private UUID insertFortuneArtifact(UUID userUuid, LocalDate fortuneDate, LocalDateTime createdAt,
        String description) {
        UUID fortuneId = UUID.randomUUID();

        artifactGalleryFixture.insertArtifact(fortuneId, "fortune", null, "fortune/thumb.png", "{}", createdAt,
            createdAt);
        artifactSubtypeFixture.insertFortuneArtifact(fortuneId, description, "fortune/card.png", userUuid, fortuneDate);

        return fortuneId;
    }

    private void insertPrompt() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        gmsPromptFixture.insertActivePromptTemplate(1L, "Daily fortune", "오늘의 운세를 JSON으로 생성해줘.", "fortune", 1L, now,
            null, 1L);
    }

    private FortuneGmsResult sampleGmsResult() {
        return new FortuneGmsResult("오늘은 흐름을 정리하는 날", "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.", 78, 66, 84, 71, "은회색", "정리",
            "동쪽", "결정은 한 템포 늦추는 것이 좋습니다.", "오늘은 정리할수록 운이 열린다", "moon", "#2C2C4A", "#C0C0C0", "moon_waning");
    }

    private FortuneGmsResult sampleGmsResultWithoutDesign() {
        return new FortuneGmsResult("오늘은 흐름을 정리하는 날", "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.", 78, 66, 84, 71, "은회색", "정리",
            "동쪽", "결정은 한 템포 늦추는 것이 좋습니다.", "오늘은 정리할수록 운이 열린다", null, null, null, null);
    }

    private FortuneGmsResult sampleGmsResultWithLongCaution() {
        return new FortuneGmsResult("오늘은 흐름을 정리하는 날", "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.", 78, 66, 84, 71, "은회색", "정리",
            "동쪽", "마음이 먼저 앞서면 흐름이 꼬일 수 있으니, 중요한 결정은 한 템포 늦추고 한 번 더 확인하는 것이 좋습니다.", "오늘은 정리할수록 운이 열린다", "moon",
            "#2C2C4A", "#C0C0C0", "moon_waning");
    }

    private FortuneGmsResult invalidGmsResult() {
        return new FortuneGmsResult("", "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.", 101, 66, 84, 71, "은회색", "정리", "동쪽",
            "결정은 한 템포 늦추는 것이 좋습니다.", "오늘은 정리할수록 운이 열린다", null, null, null, null);
    }

    private String sajuRequestBody() {
        return """
            {
              "calendarType": "solar",
              "yearPillar": "임신",
              "monthPillar": "경술",
              "dayPillar": "계유",
              "hourPillar": "을묘",
              "dayMasterElement": "수",
              "dayBranchElement": "금",
              "dayMasterYinYang": "음",
              "dayBranchYinYang": "음"
            }
            """;
    }

    private String sajuRequestBodyWithoutHourPillar() {
        return """
            {
              "calendarType": "solar",
              "yearPillar": "임신",
              "monthPillar": "경술",
              "dayPillar": "계유",
              "dayMasterElement": "수",
              "dayBranchElement": "금",
              "dayMasterYinYang": "음",
              "dayBranchYinYang": "음"
            }
            """;
    }

    private String storedFortuneDescription() {
        return """
            {
              "calendarType": "solar",
              "yearPillar": "임신",
              "monthPillar": "경술",
              "dayPillar": "계유",
              "hourPillar": null,
              "dayMasterElement": "수",
              "dayBranchElement": "금",
              "dayMasterYinYang": "음",
              "dayBranchYinYang": "음",
              "saju": {
                "calendarType": "solar",
                "yearPillar": "임신",
                "monthPillar": "경술",
                "dayPillar": "계유",
                "hourPillar": null,
                "dayMasterElement": "수",
                "dayBranchElement": "금",
                "dayMasterYinYang": "음",
                "dayBranchYinYang": "음"
              },
              "title": "오늘은 흐름을 정리하는 날",
              "summary": "차분하게 우선순위를 세우면 좋은 결과가 나는 하루입니다.",
              "overallLuck": 78,
              "loveLuck": 66,
              "workLuck": 84,
              "moneyLuck": 71,
              "luckyColor": "은회색",
              "luckyKeyword": "정리",
              "luckyDirection": "동쪽",
              "caution": "결정은 한 템포 늦추는 것이 좋습니다.",
              "postitLine": "오늘은 정리할수록 운이 열린다",
              "cardTheme": "moon",
              "bgColor": "#2C2C4A",
              "accentColor": "#C0C0C0",
              "iconKey": "moon_waning"
            }
            """;
    }

    private String nextAvailableAt(LocalDate today) {
        return today.plusDays(1).atStartOfDay(KST_ZONE).toOffsetDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }
}
