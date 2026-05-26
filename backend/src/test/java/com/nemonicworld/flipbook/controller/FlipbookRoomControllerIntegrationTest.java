package com.nemonicworld.flipbook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.RoomCodeGenerationException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.support.AbstractIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 플립북 방 생성 API의 HTTP 응답과 Redis 저장 경계를 검증합니다.
 */
class FlipbookRoomControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "FB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RoomCodeGenerator roomCodeGenerator;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        prepareBackofficeSettingTables();
        jdbcTemplate.update("DELETE FROM backoffice_setting");
        userRepository.deleteAll();

        valueOperations = createValueOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(stringRedisTemplate.hasKey(anyString())).willReturn(false);
        given(roomCodeGenerator.generateUnique(any())).willReturn(DEFAULT_ROOM_CODE);
    }

    /**
     * 유효한 기존 익명 사용자는 방장 겸 첫 참여자로 Redis 대기 방을 생성합니다.
     */
    @Test
    void createFlipbookRoomReturnsCreatedResponseAndStoresWaitingRoomInRedis() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        MvcResult result = mockMvc
            .perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 방 생성 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("WAITING"))
            .andExpect(jsonPath("$.data.hostUserUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(45))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.default").value(45))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[0]").value(30))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[1]").value(45))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[2]").value(60))
            .andExpect(jsonPath("$.data.minParticipants").value(2))
            .andExpect(jsonPath("$.data.maxParticipants").value(6))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.participants[0].userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.participants[0].nickname").value("망고"))
            .andExpect(jsonPath("$.data.participants[0].host").value(true))
            .andExpect(jsonPath("$.data.participants[0].joinOrder").value(0))
            .andExpect(jsonPath("$.data.participants[0].connected").value(false))
            .andExpect(jsonPath("$.data.createdAt").exists()).andReturn();

        JsonNode responseData = readData(result);
        JsonNode storedRoom = readStoredJson("flipbook:room:%s".formatted(DEFAULT_ROOM_CODE));
        JsonNode storedInvite = readStoredJson("invite:%s".formatted(DEFAULT_ROOM_CODE));

        assertThat(storedRoom.path("roomCode").asText()).isEqualTo(DEFAULT_ROOM_CODE);
        assertThat(storedRoom.path("status").asText()).isEqualTo("WAITING");
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(userUuid.toString());
        assertThat(storedRoom.path("timeLimitSeconds").asInt()).isEqualTo(45);
        assertThat(storedRoom.path("minParticipants").asInt()).isEqualTo(2);
        assertThat(storedRoom.path("maxParticipants").asInt()).isEqualTo(6);
        assertThat(storedRoom.path("participants")).hasSize(1);
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(userUuid.toString());
        assertThat(storedRoom.path("participants").get(0).path("nickname").asText()).isEqualTo("망고");
        assertThat(storedRoom.path("participants").get(0).path("host").asBoolean()).isTrue();
        assertThat(storedRoom.path("participants").get(0).path("joinOrder").asInt()).isZero();
        assertThat(storedRoom.path("participants").get(0).path("connected").asBoolean()).isFalse();
        assertThat(storedRoom.path("participants").get(0).path("joinedAt").asText()).isNotBlank();
        assertThat(storedRoom.path("kickedUserUuids")).isEmpty();
        assertThat(storedRoom.path("createdAt").asText()).isEqualTo(responseData.path("createdAt").asText());
        assertThat(storedRoom.path("updatedAt").asText()).isEqualTo(responseData.path("createdAt").asText());
        assertThat(storedInvite.path("inviteCode").asText()).isEqualTo(DEFAULT_ROOM_CODE);
        assertThat(storedInvite.path("boothType").asText()).isEqualTo("flipbook");
        assertThat(storedInvite.path("roomId").asText()).isEqualTo(DEFAULT_ROOM_CODE);
        assertThat(storedInvite.path("roomName").asText()).isEqualTo("망고의 플립북");
        assertThat(storedInvite.path("expiresAt").asText()).isNotBlank();
    }

    @Test
    void createFlipbookRoomUsesFlipbookRuntimeSettingsForNewRoom() throws Exception {
        insertFlipbookParticipantLimitSetting("""
            {"min":3,"max":8,"unit":"people","description":"Flipbook room participant limit"}
            """);
        insertFlipbookRoomTimeLimitSetting("""
            {"default":60,"allowed":[45,60,90],"unit":"seconds","description":"Flipbook room time limit"}
            """);
        UUID userUuid = createExistingUserWithNickname("Mango");

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.timeLimitSeconds").value(60))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.default").value(60))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[0]").value(45))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[1]").value(60))
            .andExpect(jsonPath("$.data.timeLimitSecondsOptions.allowed[2]").value(90))
            .andExpect(jsonPath("$.data.minParticipants").value(3))
            .andExpect(jsonPath("$.data.maxParticipants").value(8));

        JsonNode storedRoom = readStoredJson("flipbook:room:%s".formatted(DEFAULT_ROOM_CODE));
        assertThat(storedRoom.path("timeLimitSeconds").asInt()).isEqualTo(60);
        assertThat(storedRoom.path("minParticipants").asInt()).isEqualTo(3);
        assertThat(storedRoom.path("maxParticipants").asInt()).isEqualTo(8);
    }

    /**
     * 방코드는 공통 초대코드 Redis key 기준으로 중복 여부를 확인합니다.
     */
    @Test
    void createFlipbookRoomRetriesWhenGeneratedRoomCodeAlreadyExistsInInviteRedis() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");
        given(stringRedisTemplate.hasKey("invite:AAAAAA")).willReturn(true);
        given(stringRedisTemplate.hasKey("invite:BBBBBB")).willReturn(false);
        given(roomCodeGenerator.generateUnique(any())).willAnswer(invocation -> {
            Predicate<String> existingCodePredicate = invocation.getArgument(0);

            if (existingCodePredicate.test("AAAAAA")) {
                assertThat(existingCodePredicate.test("BBBBBB")).isFalse();
                return "BBBBBB";
            }

            return "AAAAAA";
        });

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roomCode").value("BBBBBB"));

        verify(stringRedisTemplate).hasKey("invite:AAAAAA");
        verify(stringRedisTemplate).hasKey("invite:BBBBBB");
        verify(valueOperations).set(eq("flipbook:room:BBBBBB"), anyString(), eq(ROOM_STATE_TTL));
        verify(valueOperations).set(eq("invite:BBBBBB"), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 방 생성은 사용자 방문 메타데이터를 갱신하지 않는 읽기 전용 사용자 조회만 수행합니다.
     */
    @Test
    void createFlipbookRoomDoesNotUpdateUserMetadata() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");
        AppUser beforeUser = userRepository.findById(userUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isCreated());

        AppUser afterUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
    }

    /**
     * 방 생성 전 닉네임이 기본값이면 기존 닉네임 설정 API를 먼저 호출해야 합니다.
     */
    @Test
    void createFlipbookRoomRejectsDefaultNicknameAndDoesNotStoreRoom() throws Exception {
        UUID userUuid = createExistingUserWithDefaultNickname();

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        assertThat(userRepository.count()).isEqualTo(1);
        verify(roomCodeGenerator, never()).generateUnique(any());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 공백 닉네임도 방장 표시에 사용할 수 없으므로 방 생성을 거부합니다.
     */
    @Test
    void createFlipbookRoomRejectsBlankNicknameAndDoesNotStoreRoom() throws Exception {
        UUID userUuid = createExistingUserWithNickname("   ");

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        assertThat(userRepository.count()).isEqualTo(1);
        verify(roomCodeGenerator, never()).generateUnique(any());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * UUID 헤더가 없으면 기존 공통 UUID 오류 메시지로 400 응답을 반환합니다.
     */
    @Test
    void createFlipbookRoomRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(post("/api/v1/flipbook/rooms")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * UUID 형식이 잘못되면 400 응답을 반환하고 새 사용자를 만들지 않습니다.
     */
    @Test
    void createFlipbookRoomRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * UUID 형식은 맞지만 사용자가 없으면 404를 반환하고 사용자를 생성하지 않습니다.
     */
    @Test
    void createFlipbookRoomReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 방코드 생성 재시도 한도 초과는 내부 상세 메시지를 노출하지 않고 서버 오류로 응답합니다.
     */
    @Test
    void createFlipbookRoomReturnsServerErrorWhenRoomCodeGenerationFails() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");
        given(roomCodeGenerator.generateUnique(any())).willThrow(new RoomCodeGenerationException("방코드 생성에 실패했습니다."));

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * Redis 저장에 실패하면 방 생성 전체가 실패합니다.
     */
    @Test
    void createFlipbookRoomReturnsServerErrorWhenRedisSaveFails() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");
        willThrow(new RedisConnectionFailureException("redis down")).given(valueOperations)
            .set(eq("flipbook:room:%s".formatted(DEFAULT_ROOM_CODE)), anyString(), eq(ROOM_STATE_TTL));

        mockMvc.perform(post("/api/v1/flipbook/rooms").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    private void prepareBackofficeSettingTables() {
        new AdminUserTestFixture(jdbcTemplate).ensureTable();
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS backoffice_setting (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                setting_key VARCHAR(128) NOT NULL UNIQUE,
                setting_value TEXT NOT NULL DEFAULT '{}',
                updated_by BIGINT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
    }

    private void insertFlipbookParticipantLimitSetting(String settingValue) {
        insertSetting(10L, "flipbook.room_participant_limit", settingValue);
    }

    private void insertFlipbookRoomTimeLimitSetting(String settingValue) {
        insertSetting(11L, "flipbook.room_time_limit_seconds", settingValue);
    }

    private void insertSetting(long id, String key, String settingValue) {
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO backoffice_setting (
                id,
                setting_key,
                setting_value,
                updated_by,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, key, settingValue, 0L, Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    private UUID createExistingUserWithNickname(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, updatedAt);
        userRepository.saveAndFlush(appUser);

        return userUuid;
    }

    private UUID createExistingUserWithDefaultNickname() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode readStoredJson(String expectedKey) throws Exception {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).set(keyCaptor.capture(), jsonCaptor.capture(), eq(ROOM_STATE_TTL));

        for (int index = 0; index < keyCaptor.getAllValues().size(); index++) {
            if (expectedKey.equals(keyCaptor.getAllValues().get(index))) {
                return objectMapper.readTree(jsonCaptor.getAllValues().get(index));
            }
        }

        throw new AssertionError("Redis 저장 key를 찾을 수 없습니다. expectedKey=" + expectedKey);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
