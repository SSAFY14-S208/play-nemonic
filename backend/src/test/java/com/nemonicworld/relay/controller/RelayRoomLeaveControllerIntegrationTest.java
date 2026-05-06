package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
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
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 릴레이 대기실 자발적 퇴장 API의 HTTP 계약과 Redis 상태 변경을 검증합니다.
 */
class RelayRoomLeaveControllerIntegrationTest {

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
     * 일반 참여자가 WAITING 방에서 퇴장하면 참여자 목록에서 제거하고 방장은 그대로 유지합니다.
     */
    @Test
    void leaveRelayRoomParticipantRemovesRequesterAndKeepsHost() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID leaverUuid = createExistingUserWithNickname("포도");
        UUID remainingUuid = createExistingUserWithNickname("사과");
        UUID kickedUuid = UUID.randomUUID();
        RelayRoomState originalRoomState = createRoomState(RelayRoomStatus.WAITING,
            participant(hostUuid, "망고", true, 0), participant(leaverUuid, "포도", false, 1),
            participant(remainingUuid, "사과", false, 3));
        RelayRoomState roomState = originalRoomState.withParticipantsAndKickedUserUuids(
            originalRoomState.participants(), List.of(kickedUuid.toString()), originalRoomState.updatedAt());
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        performLeave(leaverUuid.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 방 퇴장 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.leftUserUuid").value(leaverUuid.toString()))
            .andExpect(jsonPath("$.data.leftNickname").value("포도"))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.hostChanged").value(false))
            .andExpect(jsonPath("$.data.newHostUserUuid").doesNotExist())
            .andExpect(jsonPath("$.data.roomClosed").value(false))
            .andExpect(jsonPath("$.data.roomStatus").value("WAITING"))
            .andExpect(jsonPath("$.data.leftAt").isNotEmpty());

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("WAITING");
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(hostUuid.toString());
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(hostUuid.toString());
        assertThat(storedRoom.path("participants").get(0).path("host").asBoolean()).isTrue();
        assertThat(storedRoom.path("participants").get(0).path("joinOrder").asInt()).isZero();
        assertThat(storedRoom.path("participants").get(1).path("userUuid").asText())
            .isEqualTo(remainingUuid.toString());
        assertThat(storedRoom.path("participants").get(1).path("joinOrder").asInt()).isEqualTo(3);
        assertThat(storedRoom.path("kickedUserUuids").get(0).asText()).isEqualTo(kickedUuid.toString());
        assertThat(LocalDateTime.parse(storedRoom.path("updatedAt").asText())).isNotEqualTo(roomState.updatedAt());

        ArgumentCaptor<RelayRoomLeaveResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomLeaveResponse.class);
        verify(relayRoomEventPublisher).publishParticipantLeft(eventCaptor.capture());
        assertThat(eventCaptor.getValue().leftUserUuid()).isEqualTo(leaverUuid.toString());
        verify(relayRoomEventPublisher, never()).publishHostChanged(any(RelayRoomLeaveResponse.class));
        verify(relayRoomEventPublisher, never()).publishRoomClosed(anyString(), any(LocalDateTime.class));
        verify(relayRoomEventPublisher).closeLeftRoomSession(DEFAULT_ROOM_CODE, leaverUuid.toString());
    }

    /**
     * 방장이 WAITING 방에서 퇴장하면 joinOrder가 가장 작은 남은 참여자에게 방장을 승계합니다.
     */
    @Test
    void leaveRelayRoomHostTransfersHostToLowestJoinOrderParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID laterUuid = createExistingUserWithNickname("포도");
        UUID nextHostUuid = createExistingUserWithNickname("사과");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0),
            participant(laterUuid, "포도", false, 5), participant(nextHostUuid, "사과", false, 2)));

        performLeave(hostUuid.toString()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.hostChanged").value(true))
            .andExpect(jsonPath("$.data.newHostUserUuid").value(nextHostUuid.toString()))
            .andExpect(jsonPath("$.data.newHostNickname").value("사과"))
            .andExpect(jsonPath("$.data.roomClosed").value(false))
            .andExpect(jsonPath("$.data.roomStatus").value("WAITING"));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("WAITING");
        assertThat(storedRoom.path("hostUserUuid").asText()).isEqualTo(nextHostUuid.toString());
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(laterUuid.toString());
        assertThat(storedRoom.path("participants").get(0).path("host").asBoolean()).isFalse();
        assertThat(storedRoom.path("participants").get(0).path("joinOrder").asInt()).isEqualTo(5);
        assertThat(storedRoom.path("participants").get(1).path("userUuid").asText()).isEqualTo(nextHostUuid.toString());
        assertThat(storedRoom.path("participants").get(1).path("host").asBoolean()).isTrue();
        assertThat(storedRoom.path("participants").get(1).path("joinOrder").asInt()).isEqualTo(2);

        verify(relayRoomEventPublisher).publishParticipantLeft(any(RelayRoomLeaveResponse.class));
        verify(relayRoomEventPublisher).publishHostChanged(any(RelayRoomLeaveResponse.class));
        verify(relayRoomEventPublisher, never()).publishRoomClosed(anyString(), any(LocalDateTime.class));
        verify(relayRoomEventPublisher).closeLeftRoomSession(DEFAULT_ROOM_CODE, hostUuid.toString());
    }

    /**
     * 마지막 참여자가 퇴장하면 방은 CLOSED가 되고 cleanup은 기존 scheduler에 맡깁니다.
     */
    @Test
    void leaveRelayRoomLastParticipantClosesRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0)));

        performLeave(hostUuid.toString()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participantCount").value(0))
            .andExpect(jsonPath("$.data.hostChanged").value(false))
            .andExpect(jsonPath("$.data.newHostUserUuid").doesNotExist())
            .andExpect(jsonPath("$.data.roomClosed").value(true))
            .andExpect(jsonPath("$.data.roomStatus").value("CLOSED"));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("CLOSED");
        assertThat(storedRoom.path("hostUserUuid").isNull()).isTrue();
        assertThat(storedRoom.path("participants")).isEmpty();
        assertThat(storedRoom.path("kickedUserUuids")).isEmpty();

        verify(relayRoomEventPublisher).publishParticipantLeft(any(RelayRoomLeaveResponse.class));
        verify(relayRoomEventPublisher, never()).publishHostChanged(any(RelayRoomLeaveResponse.class));
        verify(relayRoomEventPublisher).publishRoomClosed(eq(DEFAULT_ROOM_CODE), any(LocalDateTime.class));
        verify(relayRoomEventPublisher).closeLeftRoomSession(DEFAULT_ROOM_CODE, hostUuid.toString());
    }

    /**
     * 자발적으로 퇴장한 사용자는 강퇴 목록에 없으므로 WAITING 방에 다시 입장할 수 있습니다.
     */
    @Test
    void joinRelayRoomAllowsVoluntarilyLeftUserWhenNotKicked() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID leaverUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, leaverUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.participantCount").value(2));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("participants").get(1).path("userUuid").asText()).isEqualTo(leaverUuid.toString());
        assertThat(storedRoom.path("kickedUserUuids")).isEmpty();
    }

    /**
     * 방 참여자가 아닌 사용자는 퇴장 API를 사용할 수 없습니다.
     */
    @Test
    void leaveRelayRoomRejectsNonParticipantRequester() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID requesterUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0)));

        performLeave(requesterUuid.toString()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 방에 참여하지 않은 사용자입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishParticipantLeft(any(RelayRoomLeaveResponse.class));
    }

    /**
     * 요청자 UUID 형식이 잘못되면 Redis 조회 없이 400을 반환합니다.
     */
    @Test
    void leaveRelayRoomRejectsInvalidUserUuid() throws Exception {
        performLeave("not-a-uuid").andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 존재하지 않는 roomCode는 기존 방 없음 메시지로 응답합니다.
     */
    @Test
    void leaveRelayRoomReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        performLeave(userUuid.toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 게임 시작 이후 상태에서는 자발적 퇴장 API를 사용할 수 없습니다.
     */
    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"PLAYING", "FINALIZING", "FINISHED"})
    void leaveRelayRoomRejectsNonWaitingRoom(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(roomStatus, participant(hostUuid, "망고", true, 0)));

        performLeave(hostUuid.toString()).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("대기실에서만 퇴장할 수 있습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 이미 CLOSED인 방에서는 이미 종료된 방 메시지로 응답합니다.
     */
    @Test
    void leaveRelayRoomRejectsClosedRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.CLOSED, participant(hostUuid, "망고", true, 0)));

        performLeave(hostUuid.toString()).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이미 종료된 방입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
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

    private ResultActions performLeave(String requesterUuid) throws Exception {
        return mockMvc.perform(delete("/api/v1/relay/rooms/{roomCode}/participants/me", DEFAULT_ROOM_CODE)
            .header(ANONYMOUS_USER_UUID_HEADER, requesterUuid));
    }

    private void storeRoom(String roomCode, RelayRoomState roomState) throws Exception {
        given(valueOperations.get("relay:room:%s".formatted(roomCode)))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private RelayRoomState createRoomState(RelayRoomStatus status, RelayRoomParticipant... participants) {
        List<RelayRoomParticipant> participantList = new ArrayList<>(List.of(participants));
        String hostUserUuid = participantList.stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participantList.get(0).userUuid());

        return createRoomStateWithHostUserUuid(hostUserUuid, status, participants);
    }

    private RelayRoomState createRoomStateWithHostUserUuid(String hostUserUuid, RelayRoomStatus status,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomParticipant> participantList = new ArrayList<>(List.of(participants));

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, 60, 2, 6, null, participantList, createdAt,
            createdAt.plusSeconds(1));
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
}
