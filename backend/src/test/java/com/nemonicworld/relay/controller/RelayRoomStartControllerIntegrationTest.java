package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 릴레이 게임 시작 API의 HTTP 계약, Redis 상태 변경 범위, WebSocket 이벤트 발행을 검증합니다.
 */
class RelayRoomStartControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
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

    @Test
    void startRelayGameStartsTwoParticipantRoomAndStoresAssignments() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState originalRoomState = createRoomState(RelayRoomStatus.WAITING, 45,
            participant(hostUuid, "Mango", true, 0, true), participant(participantUuid, "Peach", false, 1, true));
        storeRoom(DEFAULT_ROOM_CODE, originalRoomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 게임 시작 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("PLAYING"))
            .andExpect(jsonPath("$.data.hostUserUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(45))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.currentPart").value("FACE"))
            .andExpect(jsonPath("$.data.assignmentCount").value(6)).andExpect(jsonPath("$.data.partStartedAt").exists())
            .andExpect(jsonPath("$.data.partDeadlineAt").exists()).andExpect(jsonPath("$.data.gameStartedAt").exists())
            .andExpect(jsonPath("$.data.viewer.userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.host").value(true));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("PLAYING");
        assertThat(storedRoom.path("currentPart").asText()).isEqualTo("FACE");
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(originalRoomState.hostUserUuid());
        assertThat(storedRoom.path("timeLimitSeconds").asInt()).isEqualTo(45);
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(hostUuid.toString());
        assertThat(storedRoom.path("createdAt").asText()).isEqualTo(originalRoomState.createdAt().toString());
        assertThat(storedRoom.path("updatedAt").asText()).isNotEqualTo(originalRoomState.updatedAt().toString());

        JsonNode assignments = storedRoom.path("assignments");
        assertThat(assignments).hasSize(6);
        for (JsonNode assignment : assignments) {
            assertThat(assignment.path("status").asText()).isEqualTo("PENDING");
            assertThat(assignment.get("fileId").isNull()).isTrue();
            assertThat(assignment.get("objectKey").isNull()).isTrue();
            assertThat(assignment.get("hintObjectKey").isNull()).isTrue();
            assertThat(assignment.get("submittedAt").isNull()).isTrue();
            assertThat(assignment.path("empty").asBoolean()).isFalse();
            assertThat(assignment.path("autoSubmitted").asBoolean()).isFalse();
        }

        LocalDateTime partStartedAt = LocalDateTime.parse(storedRoom.path("partStartedAt").asText());
        LocalDateTime partDeadlineAt = LocalDateTime.parse(storedRoom.path("partDeadlineAt").asText());
        assertThat(Duration.between(partStartedAt, partDeadlineAt)).isEqualTo(Duration.ofSeconds(45));
        assertThat(storedRoom.path("gameStartedAt").asText()).isEqualTo(storedRoom.path("partStartedAt").asText());

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();

        ArgumentCaptor<RelayRoomStateResponse> eventResponseCaptor = ArgumentCaptor
            .forClass(RelayRoomStateResponse.class);
        verify(relayRoomEventPublisher).publishGameStarted(eventResponseCaptor.capture());
        assertThat(eventResponseCaptor.getValue().status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(eventResponseCaptor.getValue().assignmentCount()).isEqualTo(6);
        verify(relayRoomEventPublisher).publishPartStarted(any(RelayRoomStateResponse.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 45, 60})
    void startRelayGameUsesStoredTimeLimitForDeadline(int timeLimitSeconds) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, timeLimitSeconds,
            participant(hostUuid, "Mango", true, 0, true), participant(participantUuid, "Peach", false, 1, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.timeLimitSeconds").value(timeLimitSeconds))
            .andExpect(jsonPath("$.data.status").value("PLAYING"));

        JsonNode storedRoom = readSavedRoom();
        LocalDateTime partStartedAt = LocalDateTime.parse(storedRoom.path("partStartedAt").asText());
        LocalDateTime partDeadlineAt = LocalDateTime.parse(storedRoom.path("partDeadlineAt").asText());
        assertThat(Duration.between(partStartedAt, partDeadlineAt)).isEqualTo(Duration.ofSeconds(timeLimitSeconds));
    }

    @Test
    void startRelayGameCreatesShiftedAssignmentsForThreeParticipants() throws Exception {
        UUID aUuid = createExistingUserWithNickname("A");
        UUID bUuid = createExistingUserWithNickname("B");
        UUID cUuid = createExistingUserWithNickname("C");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 45, participant(bUuid, "B", false, 1, true),
                participant(aUuid, "A", true, 0, true), participant(cUuid, "C", false, 2, true)));

        mockMvc.perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE)
            .header(ANONYMOUS_USER_UUID_HEADER, aUuid.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignmentCount").value(9));

        JsonNode assignments = readSavedRoom().path("assignments");
        assertAssignment(assignments.get(0), 0, "FACE", aUuid);
        assertAssignment(assignments.get(1), 0, "BODY", bUuid);
        assertAssignment(assignments.get(2), 0, "LEGS", cUuid);
        assertAssignment(assignments.get(3), 1, "FACE", bUuid);
        assertAssignment(assignments.get(4), 1, "BODY", cUuid);
        assertAssignment(assignments.get(5), 1, "LEGS", aUuid);
        assertAssignment(assignments.get(6), 2, "FACE", cUuid);
        assertAssignment(assignments.get(7), 2, "BODY", aUuid);
        assertAssignment(assignments.get(8), 2, "LEGS", bUuid);
    }

    @Test
    void startRelayGameRejectsNonHostParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, 45,
            participant(hostUuid, "Mango", true, 0, true), participant(participantUuid, "Peach", false, 1, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                participantUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("방장만 사용할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
        verify(relayRoomEventPublisher, never()).publishPartStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID viewerUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 45, participant(hostUuid, "Mango", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 방에 참여하지 않은 사용자입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsNotEnoughParticipants() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, 45, participant(hostUuid, "Mango", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("최소 2명이 모여야 시작할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsDisconnectedParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, 45,
            participant(hostUuid, "Mango", true, 0, true), participant(participantUuid, "Peach", false, 1, false)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("모든 참여자가 연결된 상태에서만 시작할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsAlreadyPlayingRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.PLAYING, 45,
            participant(hostUuid, "Mango", true, 0, true), participant(participantUuid, "Peach", false, 1, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이미 게임이 시작되었습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"FINISHED", "CLOSED"})
    void startRelayGameRejectsClosedRooms(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(roomStatus, 45, participant(hostUuid, "Mango", true, 0, true),
            participant(participantUuid, "Peach", false, 1, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이미 종료된 방입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    @Test
    void startRelayGameRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameRejectsInvalidRoomCode() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", "not-a-room").header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 방코드입니다."));

        verify(valueOperations, never()).get(anyString());
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
    }

    @Test
    void startRelayGameReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/start", "ZZZZZZ").header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishGameStarted(any(RelayRoomStateResponse.class));
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

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected,
            connected ? null : now, now);
    }

    private JsonNode readSavedRoom() throws Exception {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(ROOM_STATE_TTL));

        assertThat(keyCaptor.getValue()).isEqualTo("relay:room:%s".formatted(DEFAULT_ROOM_CODE));

        return objectMapper.readTree(jsonCaptor.getValue());
    }

    private void assertAssignment(JsonNode assignment, int canvasIndex, String part, UUID assignedUserUuid) {
        assertThat(assignment.path("canvasIndex").asInt()).isEqualTo(canvasIndex);
        assertThat(assignment.path("part").asText()).isEqualTo(part);
        assertThat(assignment.path("assignedUserUuid").asText()).isEqualTo(assignedUserUuid.toString());
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
