package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RelayRoomCloseControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);
    private static final String SUCCESS_MESSAGE = "릴레이 방 종료 성공";
    private static final String ALREADY_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String HOST_REQUIRED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";

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
        jdbcTemplate.update("DELETE FROM file_upload");
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

    @Test
    void closeFinishedRoomByHostChangesStatusToClosed() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = room(RelayRoomStatus.FINISHED, participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.roomStatus").value("CLOSED")).andExpect(jsonPath("$.data.closedAt").exists())
            .andExpect(jsonPath("$.data.alreadyClosed").value(false));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("CLOSED");
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(roomState.hostUserUuid());
        assertThat(LocalDateTime.parse(storedRoom.path("createdAt").asText())).isEqualTo(roomState.createdAt());
        assertThat(LocalDateTime.parse(storedRoom.path("updatedAt").asText())).isNotEqualTo(roomState.updatedAt());

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeUser.getLastSeenAt());
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUser.getUpdatedAt());
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUser.getUserAgent());
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();
        verify(relayRoomEventPublisher).publishRoomClosed(eq(DEFAULT_ROOM_CODE), any(LocalDateTime.class));
    }

    @Test
    void closeAlreadyClosedRoomReturnsIdempotentResponseWithoutEvent() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        RelayRoomState roomState = room(RelayRoomStatus.CLOSED, participant(hostUuid, "Mango", true, 0));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value(ALREADY_CLOSED_MESSAGE))
            .andExpect(jsonPath("$.data.roomStatus").value("CLOSED"))
            .andExpect(jsonPath("$.data.closedAt").value(roomState.updatedAt().toString()))
            .andExpect(jsonPath("$.data.alreadyClosed").value(true));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomRejectsNonHostParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, room(RelayRoomStatus.FINISHED, participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                participantUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(HOST_REQUIRED_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomRejectsNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID viewerUuid = createExistingUserWithNickname("Berry");
        storeRoom(DEFAULT_ROOM_CODE, room(RelayRoomStatus.FINISHED, participant(hostUuid, "Mango", true, 0)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @ParameterizedTest
    @CsvSource({"WAITING,결과 생성 전에는 방을 종료할 수 없습니다.", "PLAYING,게임 진행 중에는 방을 종료할 수 없습니다.",
        "FINALIZING,결과 생성 중에는 방을 종료할 수 없습니다."})
    void closeRoomRejectsNonFinishedRooms(RelayRoomStatus roomStatus, String expectedMessage) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        storeRoom(DEFAULT_ROOM_CODE, room(roomStatus, participant(hostUuid, "Mango", true, 0)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(expectedMessage));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_UUID_MESSAGE));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(USER_NOT_FOUND_MESSAGE));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomRejectsInvalidRoomCode() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", "not-a-room").header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_ROOM_CODE_MESSAGE));

        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void closeRoomReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/close", "ZZZZZZ").header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_NOT_FOUND_MESSAGE));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relayRoomEventPublisher);
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

    private RelayRoomState room(RelayRoomStatus status, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(20).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime updatedAt = createdAt.plusMinutes(15);
        String hostUserUuid = List.of(participants).stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participants[0].userUuid());

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, 45, 2, 6, RelayDrawingPart.LEGS,
            List.of(participants), List.of(), createdAt.plusMinutes(1), createdAt.plusMinutes(2),
            createdAt.plusMinutes(1), createdAt, updatedAt);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS));
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
