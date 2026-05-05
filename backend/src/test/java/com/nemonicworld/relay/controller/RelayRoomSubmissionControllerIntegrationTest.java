package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.RelaySubmissionStorage;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RelayRoomSubmissionControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);
    private static final String SUCCESS_MESSAGE = "릴레이 그림 제출 성공";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String GAME_NOT_STARTED_MESSAGE = "게임이 아직 시작되지 않았습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 그림이 없습니다.";
    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";
    private static final String HINT_IMAGE_REQUIRED_MESSAGE = "힌트 이미지가 필요합니다.";

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
    private RelaySubmissionStorage relaySubmissionStorage;

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
    void submitFaceAssignmentStoresDrawingAndHintAndUpdatesRedis() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                assignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        storeRoom(DEFAULT_ROOM_CODE, roomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.canvasIndex").value(0)).andExpect(jsonPath("$.data.part").value("FACE"))
            .andExpect(jsonPath("$.data.assignmentStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.drawingObjectKey").value("relay/tmp/AB3K9Q/0/face.png"))
            .andExpect(jsonPath("$.data.hintObjectKey").value("relay/tmp/AB3K9Q/0/face-hint.png"))
            .andExpect(jsonPath("$.data.alreadySubmitted").value(false))
            .andExpect(jsonPath("$.data.submittedCount").value(1)).andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.currentPartCompleted").value(false));

        verify(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/face.png"), any());
        verify(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/face-hint.png"), any());
        JsonNode storedRoom = readSavedRoom();
        JsonNode submittedAssignment = storedRoom.path("assignments").get(0);
        assertThat(storedRoom.path("status").asText()).isEqualTo("PLAYING");
        assertThat(storedRoom.path("currentPart").asText()).isEqualTo("FACE");
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(roomState.hostUserUuid());
        assertThat(storedRoom.path("createdAt").asText()).isEqualTo(roomState.createdAt().toString());
        assertThat(storedRoom.path("updatedAt").asText()).isNotEqualTo(roomState.updatedAt().toString());
        assertThat(submittedAssignment.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submittedAssignment.path("objectKey").asText()).isEqualTo("relay/tmp/AB3K9Q/0/face.png");
        assertThat(submittedAssignment.path("hintObjectKey").asText()).isEqualTo("relay/tmp/AB3K9Q/0/face-hint.png");
        assertThat(submittedAssignment.path("empty").asBoolean()).isFalse();
        assertThat(submittedAssignment.path("autoSubmitted").asBoolean()).isFalse();
        assertThat(submittedAssignment.path("submittedAt").isMissingNode()).isFalse();

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeUser.getLastSeenAt());
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUser.getUpdatedAt());
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUser.getUserAgent());
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();
        assertThat(countRows("file_upload")).isZero();
        verify(relayRoomEventPublisher).publishPartSubmitted(any(RelayRoomSubmissionResponse.class));
    }

    @Test
    void submitBodyAssignmentStoresDrawingAndHint() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.BODY, List.of(assignment(0, RelayDrawingPart.BODY, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "body.png")).file(pngFile("hintImage", "body-hint.png"))
                .param("canvasIndex", "0").param("part", "BODY")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.drawingObjectKey").value("relay/tmp/AB3K9Q/0/body.png"))
            .andExpect(jsonPath("$.data.hintObjectKey").value("relay/tmp/AB3K9Q/0/body-hint.png"));

        verify(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/body.png"), any());
        verify(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/body-hint.png"), any());
    }

    @Test
    void submitLegsAssignmentIgnoresHintImage() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.LEGS, List.of(assignment(0, RelayDrawingPart.LEGS, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "legs.png")).file(pngFile("hintImage", "legs-hint.png"))
            .param("canvasIndex", "0").param("part", "LEGS").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hintObjectKey").doesNotExist())
            .andExpect(jsonPath("$.data.currentPartCompleted").value(true));

        verify(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/legs.png"), any());
        verify(relaySubmissionStorage, never()).upload(eq("relay/tmp/AB3K9Q/0/legs-hint.png"), any());
    }

    @Test
    void submitLastPendingAssignmentReturnsCurrentPartCompleted() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE,
                List.of(assignment(0, RelayDrawingPart.FACE, hostUuid),
                    assignment(1, RelayDrawingPart.FACE, participantUuid, RelayAssignmentStatus.SUBMITTED,
                        "relay/tmp/AB3K9Q/1/face.png", "relay/tmp/AB3K9Q/1/face-hint.png", false, false)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.submittedCount").value(2))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.currentPartCompleted").value(true));
    }

    @Test
    void submitAlreadySubmittedAssignmentReturnsExistingSubmissionWithoutOverwrite() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE,
                List.of(assignment(0, RelayDrawingPart.FACE, hostUuid, RelayAssignmentStatus.SUBMITTED,
                    "relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png", false, false)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "new-face.png")).file(pngFile("hintImage", "new-face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadySubmitted").value(true))
            .andExpect(jsonPath("$.data.drawingObjectKey").value("relay/tmp/AB3K9Q/0/face.png"))
            .andExpect(jsonPath("$.data.hintObjectKey").value("relay/tmp/AB3K9Q/0/face-hint.png"));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    @Test
    void submitFaceAssignmentRejectsMissingHintImage() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(HINT_IMAGE_REQUIRED_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    @Test
    void submitAssignmentRejectsMissingDrawingImage() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("hintImage", "face-hint.png")).param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsEmptyDrawingImage() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(new MockMultipartFile("drawingImage", "face.png", "image/png", new byte[0]))
                .file(pngFile("hintImage", "face-hint.png")).param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsUnsupportedContentType() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(new MockMultipartFile("drawingImage", "face.txt", "text/plain",
                    "drawing".getBytes(StandardCharsets.UTF_8)))
                .file(pngFile("hintImage", "face-hint.png")).param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(INVALID_UUID_MESSAGE));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(USER_NOT_FOUND_MESSAGE));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        UUID viewerUuid = createExistingUserWithNickname("Berry");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsWaitingRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            waitingRoom(participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(GAME_NOT_STARTED_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"FINISHED", "CLOSED"})
    void submitAssignmentRejectsClosedRooms(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            room(roomStatus, RelayDrawingPart.FACE, null, null, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc
            .perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
                .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
                .param("canvasIndex", "0").param("part", "FACE")
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ROOM_CLOSED_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsCanvasIndexMismatch() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "1").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ASSIGNMENT_MISMATCH_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsPartMismatch() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "BODY").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(ASSIGNMENT_MISMATCH_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsWhenCurrentAssignmentIsMissing() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(1, RelayDrawingPart.FACE, participantUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsAutoSubmittedAssignment() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE,
                List.of(assignment(0, RelayDrawingPart.FACE, hostUuid, RelayAssignmentStatus.AUTO_SUBMITTED,
                    "relay/tmp/AB3K9Q/0/face.png", null, true, true)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(AUTO_SUBMITTED_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentRejectsExpiredDeadline() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(90).truncatedTo(ChronoUnit.SECONDS);
        storeRoom(DEFAULT_ROOM_CODE,
            room(RelayRoomStatus.PLAYING, RelayDrawingPart.FACE, startedAt, startedAt.plusSeconds(45),
                List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0),
                participant(participantUuid, "Peach", false, 1)));

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(SUBMISSION_EXPIRED_MESSAGE));

        verifyNoInteractions(relaySubmissionStorage, relayRoomEventPublisher);
    }

    @Test
    void submitAssignmentDoesNotUpdateRedisWhenStorageFails() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("Mango");
        UUID participantUuid = createExistingUserWithNickname("Peach");
        storeRoom(DEFAULT_ROOM_CODE,
            playingRoom(RelayDrawingPart.FACE, List.of(assignment(0, RelayDrawingPart.FACE, hostUuid)),
                participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1)));
        willThrow(new FileStorageException("파일 저장소 처리 중 오류가 발생했습니다.", new RuntimeException()))
            .given(relaySubmissionStorage).upload(eq("relay/tmp/AB3K9Q/0/face.png"), any());

        mockMvc.perform(multipart("/api/v1/relay/rooms/{roomCode}/submissions", DEFAULT_ROOM_CODE)
            .file(pngFile("drawingImage", "face.png")).file(pngFile("hintImage", "face-hint.png"))
            .param("canvasIndex", "0").param("part", "FACE").header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishPartSubmitted(any(RelayRoomSubmissionResponse.class));
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

        return room(RelayRoomStatus.PLAYING, currentPart, partStartedAt, partStartedAt.plusSeconds(45), assignments,
            participants);
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
        return assignment(canvasIndex, part, assignedUserUuid, RelayAssignmentStatus.PENDING, null, null, false, false);
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid,
        RelayAssignmentStatus status, String objectKey, String hintObjectKey, boolean empty, boolean autoSubmitted) {
        LocalDateTime submittedAt = status == RelayAssignmentStatus.PENDING
            ? null
            : LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), status, null, objectKey,
            hintObjectKey, empty, autoSubmitted, submittedAt);
    }

    private MockMultipartFile pngFile(String name, String originalFileName) {
        return new MockMultipartFile(name, originalFileName, "image/png", "image".getBytes(StandardCharsets.UTF_8));
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
