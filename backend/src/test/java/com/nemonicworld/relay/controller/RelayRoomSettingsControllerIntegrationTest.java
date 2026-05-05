package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 릴레이 방 설정 변경 API의 HTTP 계약, Redis 저장 범위, 이벤트 발행을 검증합니다.
 */
class RelayRoomSettingsControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";

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
    private RelayRoomEventPublisher relayRoomEventPublisher;

    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        prepareArtifactTables();
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");

        valueOperations = createValueOperationsMock();
        redisOperations = createRedisOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisOperations.opsForValue()).willReturn(valueOperations);
        given(redisOperations.exec()).willReturn(List.of("OK"));
        given(stringRedisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);

            return callback.execute(redisOperations);
        });
    }

    /**
     * 방장은 제한 시간을 30초로 변경할 수 있고 Redis에는 설정과 updatedAt만 바뀐 상태가 저장됩니다.
     */
    @Test
    void updateRelayRoomSettingsChangesTimeLimitTo30Seconds() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID participantUuid = createExistingUserWithNickname("포도");
        RelayRoomState originalRoomState = createRoomState(RelayRoomStatus.WAITING, 60,
            participant(hostUuid, "망고", true, 0), participant(participantUuid, "포도", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, originalRoomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString())
                .content("""
                    {
                      "timeLimitSeconds": 30
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 방 설정 변경 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("WAITING"))
            .andExpect(jsonPath("$.data.hostUserUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(30))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.viewer.userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.host").value(true))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("timeLimitSeconds").asInt()).isEqualTo(30);
        assertThat(storedRoom.path("status").asText()).isEqualTo(originalRoomState.status().name());
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(originalRoomState.hostUserUuid());
        assertThat(storedRoom.path("participants")).hasSize(originalRoomState.participantCount());
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(hostUuid.toString());
        assertThat(storedRoom.path("createdAt").asText()).isEqualTo(originalRoomState.createdAt().toString());
        assertThat(storedRoom.path("updatedAt").asText()).isNotEqualTo(originalRoomState.updatedAt().toString());

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();

        ArgumentCaptor<RelayRoomStateResponse> eventResponseCaptor = ArgumentCaptor
            .forClass(RelayRoomStateResponse.class);
        verify(relayRoomEventPublisher).publishSettingsChanged(eventResponseCaptor.capture());
        assertThat(eventResponseCaptor.getValue().timeLimitSeconds()).isEqualTo(30);
    }

    /**
     * 방장은 제한 시간을 45초로 변경할 수 있습니다.
     */
    @Test
    void updateRelayRoomSettingsChangesTimeLimitTo45Seconds() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 60, participant(hostUuid, "망고", true, 0)));

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": 45
                }
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.timeLimitSeconds").value(45));

        assertThat(readSavedRoom().path("timeLimitSeconds").asInt()).isEqualTo(45);
        verify(relayRoomEventPublisher).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 방장은 제한 시간을 60초로 변경할 수 있습니다.
     */
    @Test
    void updateRelayRoomSettingsChangesTimeLimitTo60Seconds() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 45, participant(hostUuid, "망고", true, 0)));

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": 60
                }
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.timeLimitSeconds").value(60));

        assertThat(readSavedRoom().path("timeLimitSeconds").asInt()).isEqualTo(60);
        verify(relayRoomEventPublisher).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 같은 제한 시간으로 다시 요청해도 성공하며, 현재 정책은 updatedAt 갱신과 이벤트 발행까지 수행합니다.
     */
    @Test
    void updateRelayRoomSettingsWithSameValueStillSavesAndPublishesEvent() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomState originalRoomState = createRoomState(RelayRoomStatus.WAITING, 45,
            participant(hostUuid, "망고", true, 0));
        storeRoom(DEFAULT_ROOM_CODE, originalRoomState);

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": 45
                }
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.timeLimitSeconds").value(45));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("timeLimitSeconds").asInt()).isEqualTo(45);
        assertThat(storedRoom.path("updatedAt").asText()).isNotEqualTo(originalRoomState.updatedAt().toString());
        verify(relayRoomEventPublisher).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 방에 참여 중이어도 방장이 아니면 설정을 변경할 수 없습니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsNonHostParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID participantUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, 60, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "포도", false, 1)));

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, participantUuid.toString())
                .content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("방장만 사용할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * Redis participants에 없는 사용자는 설정을 변경할 수 없습니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 60, participant(hostUuid, "망고", true, 0)));

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString())
                .content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 방에 참여하지 않은 사용자입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 게임 시작 이후 상태에서는 설정을 변경할 수 없습니다.
     */
    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"PLAYING", "FINISHED", "CLOSED"})
    void updateRelayRoomSettingsRejectsNonWaitingRoom(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(roomStatus, 60, participant(hostUuid, "망고", true, 0)));

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": 45
                }
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("대기 중인 방에서만 설정을 변경할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 제한 시간이 null이면 허용값 오류 메시지로 거부합니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsNullTimeLimit() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": null
                }
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_TIME_LIMIT_SECONDS_MESSAGE));

        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 요청 본문이 없으면 허용값 오류 메시지로 거부합니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsMissingBody() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_TIME_LIMIT_SECONDS_MESSAGE));

        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 30, 45, 60 외 숫자는 모두 거부합니다.
     */
    @ParameterizedTest
    @ValueSource(ints = {29, 31, 44, 46, 61})
    void updateRelayRoomSettingsRejectsUnsupportedTimeLimit(int timeLimitSeconds) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        mockMvc.perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
            .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                {
                  "timeLimitSeconds": %d
                }
                """.formatted(timeLimitSeconds))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_TIME_LIMIT_SECONDS_MESSAGE));

        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * UUID 헤더가 없으면 공통 UUID 오류 메시지로 400을 반환합니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsMissingUuidHeader() throws Exception {
        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * UUID 형식이 잘못되면 새 사용자를 만들지 않고 400을 반환합니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid").content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * UUID 형식은 맞지만 DB에 없는 사용자는 생성하지 않고 404를 반환합니다.
     */
    @Test
    void updateRelayRoomSettingsReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", DEFAULT_ROOM_CODE)
                .contentType(MediaType.APPLICATION_JSON).header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString())
                .content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 방코드 형식이 맞지 않으면 Redis 조회 없이 400을 반환합니다.
     */
    @Test
    void updateRelayRoomSettingsRejectsInvalidRoomCode() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(
                patch("/api/v1/relay/rooms/{roomCode}/settings", "not-a-room").contentType(MediaType.APPLICATION_JSON)
                    .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                        {
                          "timeLimitSeconds": 45
                        }
                        """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 방코드입니다."));

        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    /**
     * 방코드 형식은 맞지만 Redis에 방 상태가 없으면 404를 반환합니다.
     */
    @Test
    void updateRelayRoomSettingsReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(patch("/api/v1/relay/rooms/{roomCode}/settings", "ZZZZZZ").contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).content("""
                    {
                      "timeLimitSeconds": 45
                    }
                    """))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishSettingsChanged(any(RelayRoomStateResponse.class));
    }

    private void prepareArtifactTables() {
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
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
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

    private void storeRoom(String roomCode, RelayRoomState roomState) throws Exception {
        given(valueOperations.get("relay:room:%s".formatted(roomCode)))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private RelayRoomState createRoomState(RelayRoomStatus status, int timeLimitSeconds,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomParticipant> participantList = new ArrayList<>(List.of(participants));
        String hostUserUuid = participantList.stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participantList.get(0).userUuid());

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, timeLimitSeconds, 2, 6, null,
            participantList, createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private JsonNode readSavedRoom() throws Exception {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(ROOM_STATE_TTL));

        assertThat(keyCaptor.getValue()).isEqualTo("relay:room:%s".formatted(DEFAULT_ROOM_CODE));

        return objectMapper.readTree(jsonCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Long.class);
    }
}
