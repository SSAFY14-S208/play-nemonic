package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import io.minio.MinioClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
class RelayRoomAssignmentControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);
    private static final String SUCCESS_MESSAGE = "내 릴레이 배정 조회 성공";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String GAME_NOT_STARTED_MESSAGE = "게임이 아직 시작되지 않았습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 그림이 없습니다.";

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

    @MockitoBean
    private MinioClient minioClient;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        prepareArtifactTables();
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");

        valueOperations = createValueOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void getMyAssignmentReturnsFaceAssignmentWithoutHint() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                assignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("roomCode").asText()).isEqualTo(DEFAULT_ROOM_CODE);
        assertThat(data.path("canvasIndex").asInt()).isZero();
        assertThat(data.path("part").asText()).isEqualTo("FACE");
        assertThat(data.path("assignmentStatus").asText()).isEqualTo("PENDING");
        assertThat(data.path("timeLimitSeconds").asInt()).isEqualTo(45);
        assertThat(LocalDateTime.parse(data.path("partStartedAt").asText())).isEqualTo(roomState.partStartedAt());
        assertThat(LocalDateTime.parse(data.path("partDeadlineAt").asText())).isEqualTo(roomState.partDeadlineAt());
        assertThat(data.path("remainingSeconds").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.get("hint").isNull()).isTrue();

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsBodyAssignmentWithFaceHint() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        String hintObjectKey = "relay/tmp/AB3K9Q/1/face-hint.png";
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY,
            List.of(assignment(1, RelayDrawingPart.FACE, participantUuid, RelayAssignmentStatus.SUBMITTED,
                hintObjectKey, false), assignment(1, RelayDrawingPart.BODY, hostUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("canvasIndex").asInt()).isEqualTo(1);
        assertThat(data.path("part").asText()).isEqualTo("BODY");
        assertThat(data.path("hint").path("previousPart").asText()).isEqualTo("FACE");
        assertThat(data.path("hint").path("canvasIndex").asInt()).isEqualTo(1);
        assertThat(data.path("hint").path("objectKey").asText()).isEqualTo(hintObjectKey);
        assertThat(data.path("hint").get("url").isNull()).isTrue();
        assertThat(data.path("hint").path("empty").asBoolean()).isFalse();
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsLegsAssignmentWithBodyHint() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        String hintObjectKey = "relay/tmp/AB3K9Q/0/body-hint.png";
        RelayRoomState roomState = playingRoom(RelayDrawingPart.LEGS,
            List.of(assignment(0, RelayDrawingPart.BODY, participantUuid, RelayAssignmentStatus.SUBMITTED,
                hintObjectKey, false), assignment(0, RelayDrawingPart.LEGS, hostUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("canvasIndex").asInt()).isZero();
        assertThat(data.path("part").asText()).isEqualTo("LEGS");
        assertThat(data.path("hint").path("previousPart").asText()).isEqualTo("BODY");
        assertThat(data.path("hint").path("objectKey").asText()).isEqualTo(hintObjectKey);
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsNullHintWhenPreviousHintIsMissing() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY,
            List.of(assignment(0, RelayDrawingPart.FACE, participantUuid, RelayAssignmentStatus.SUBMITTED, null, false),
                assignment(0, RelayDrawingPart.BODY, hostUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.get("hint").isNull()).isTrue();
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsEmptyHintWhenPreviousAssignmentIsEmpty() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY,
            List.of(assignment(0, RelayDrawingPart.FACE, participantUuid, RelayAssignmentStatus.SUBMITTED, null, true),
                assignment(0, RelayDrawingPart.BODY, hostUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("hint").path("previousPart").asText()).isEqualTo("FACE");
        assertThat(data.path("hint").get("objectKey").isNull()).isTrue();
        assertThat(data.path("hint").path("empty").asBoolean()).isTrue();
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsSubmittedAssignmentStatus() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid, RelayAssignmentStatus.SUBMITTED, null, false)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("assignmentStatus").asText()).isEqualTo("SUBMITTED");
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentReturnsZeroRemainingSecondsWhenDeadlinePassed() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, startedAt, startedAt.plusSeconds(45),
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        JsonNode data = performSuccessGet(hostUuid);

        assertThat(data.path("remainingSeconds").asLong()).isZero();
        verifyReadOnlySideEffects();
    }

    @Test
    void getMyAssignmentRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_UUID_MESSAGE));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_UUID_MESSAGE));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(USER_NOT_FOUND_MESSAGE));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentRejectsInvalidRoomCode() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", "not-a-room")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_ROOM_CODE_MESSAGE));

        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", "ZZZZZZ").header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_NOT_FOUND_MESSAGE));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentRejectsNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        UUID viewerUuid = createExistingUserWithNickname("Berry");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                assignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentRejectsWaitingRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        storeRoom(DEFAULT_ROOM_CODE,
            waitingRoom(participant(hostUuid, "Mango", true, 0), participant(UUID.randomUUID(), "Peach", false, 1)));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(GAME_NOT_STARTED_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"FINISHED", "CLOSED"})
    void getMyAssignmentRejectsClosedRooms(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = room(roomStatus, RelayDrawingPart.FACE, null, null,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_CLOSED_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    @Test
    void getMyAssignmentRejectsWhenCurrentAssignmentIsMissing() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                assignment(0, RelayDrawingPart.BODY, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
    }

    private JsonNode performSuccessGet(UUID viewerUuid) throws Exception {
        String responseBody = mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}/assignments/me", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE))
            .andExpect(jsonPath("$.data.remainingSeconds").value(greaterThanOrEqualTo(0))).andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(responseBody).path("data");
    }

    private void verifyReadOnlySideEffects() {
        verify(valueOperations).get("relay:room:%s".formatted(DEFAULT_ROOM_CODE));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(stringRedisTemplate, never()).execute(any(SessionCallback.class));
        verifyNoInteractions(relayRoomEventPublisher, minioClient);
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

    private RelayRoomState waitingRoom(RelayRoomParticipant... participants) {
        return room(RelayRoomStatus.WAITING, null, null, null, List.of(), participants);
    }

    private RelayRoomState playingRoom(RelayDrawingPart currentPart, List<RelayRoomAssignment> assignments,
        RelayRoomParticipant... participants) {
        LocalDateTime partStartedAt = LocalDateTime.now().minusSeconds(10).truncatedTo(ChronoUnit.SECONDS);

        return playingRoom(currentPart, partStartedAt, partStartedAt.plusSeconds(45), assignments, participants);
    }

    private RelayRoomState playingRoom(RelayDrawingPart currentPart, LocalDateTime partStartedAt,
        LocalDateTime partDeadlineAt, List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        return room(RelayRoomStatus.PLAYING, currentPart, partStartedAt, partDeadlineAt, assignments, participants);
    }

    private RelayRoomState room(RelayRoomStatus status, RelayDrawingPart currentPart, LocalDateTime partStartedAt,
        LocalDateTime partDeadlineAt, List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        String hostUserUuid = List.of(participants).stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participants[0].userUuid());
        LocalDateTime gameStartedAt = status == RelayRoomStatus.PLAYING ? partStartedAt : null;

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, 45, 2, 6, currentPart, List.of(participants),
            assignments, partStartedAt, partDeadlineAt, gameStartedAt, createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid) {
        return assignment(canvasIndex, part, assignedUserUuid, RelayAssignmentStatus.PENDING, null, false);
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid,
        RelayAssignmentStatus status, String hintObjectKey, boolean empty) {
        LocalDateTime submittedAt = status == RelayAssignmentStatus.SUBMITTED
            ? LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS)
            : null;

        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), status, null, null,
            hintObjectKey, empty, false, submittedAt);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Long.class);
    }
}
