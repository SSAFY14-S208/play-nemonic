package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import com.nemonicworld.support.AbstractIntegrationTest;
import com.nemonicworld.support.AppUserTestFixture;
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
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 릴레이 대기실 참여자 강퇴 API의 HTTP 계약과 Redis 상태 변경을 검증합니다.
 */
class RelayRoomKickControllerIntegrationTest extends AbstractIntegrationTest {

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
        new AppUserTestFixture(jdbcTemplate).deleteAll();

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
     * 방장이 WAITING 방의 일반 참여자를 강퇴하면 참여자 목록에서 제거하고 강퇴 목록에 UUID를 기록합니다.
     */
    @Test
    void kickRelayRoomParticipantRemovesTargetAndStoresKickedUserUuid() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID targetUuid = createExistingUserWithNickname("포도");
        UUID remainingUuid = createExistingUserWithNickname("사과");
        RelayRoomState originalRoomState = createRoomState(RelayRoomStatus.WAITING,
            participant(hostUuid, "망고", true, 0), participant(targetUuid, "포도", false, 1),
            participant(remainingUuid, "사과", false, 3));
        storeRoom(DEFAULT_ROOM_CODE, originalRoomState);

        performKick(hostUuid, targetUuid).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("참여자 강퇴 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.kickedUserUuid").value(targetUuid.toString()))
            .andExpect(jsonPath("$.data.kickedNickname").value("포도"))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.kickedAt").isNotEmpty());

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("WAITING");
        assertThat(storedRoom.path("participants")).hasSize(2);
        assertThat(storedRoom.path("participants").get(0).path("userUuid").asText()).isEqualTo(hostUuid.toString());
        assertThat(storedRoom.path("participants").get(0).path("joinOrder").asInt()).isZero();
        assertThat(storedRoom.path("participants").get(1).path("userUuid").asText())
            .isEqualTo(remainingUuid.toString());
        assertThat(storedRoom.path("participants").get(1).path("joinOrder").asInt()).isEqualTo(3);
        assertThat(storedRoom.path("kickedUserUuids").get(0).asText()).isEqualTo(targetUuid.toString());
        assertThat(LocalDateTime.parse(storedRoom.path("updatedAt").asText()))
            .isNotEqualTo(originalRoomState.updatedAt());

        ArgumentCaptor<RelayRoomKickResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomKickResponse.class);
        verify(relayRoomEventPublisher).publishParticipantKicked(eventCaptor.capture());
        assertThat(eventCaptor.getValue().kickedUserUuid()).isEqualTo(targetUuid.toString());
        verify(relayRoomEventPublisher).publishKickedFromRoom(DEFAULT_ROOM_CODE, targetUuid.toString());
    }

    /**
     * 강퇴로 최소 시작 인원 미만이 되어도 방 상태는 WAITING으로 유지합니다.
     */
    @Test
    void kickRelayRoomParticipantKeepsWaitingRoomWhenBelowMinimumParticipants() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID targetUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0),
            participant(targetUuid, "포도", false, 1)));

        performKick(hostUuid, targetUuid).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participantCount").value(1));

        JsonNode storedRoom = readSavedRoom();
        assertThat(storedRoom.path("status").asText()).isEqualTo("WAITING");
        assertThat(storedRoom.path("participants")).hasSize(1);
        assertThat(storedRoom.path("minParticipants").asInt()).isEqualTo(2);
    }

    /**
     * 방장이 아닌 참여자는 강퇴할 수 없습니다.
     */
    @Test
    void kickRelayRoomParticipantRejectsNonHostParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID participantUuid = createExistingUserWithNickname("포도");
        UUID targetUuid = createExistingUserWithNickname("사과");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0),
            participant(participantUuid, "포도", false, 1), participant(targetUuid, "사과", false, 2)));

        performKick(participantUuid, targetUuid).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("방장만 사용할 수 있는 기능입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
        verify(relayRoomEventPublisher, never()).publishParticipantKicked(any(RelayRoomKickResponse.class));
    }

    /**
     * 방 참여자가 아닌 사용자는 강퇴 API를 사용할 수 없습니다.
     */
    @Test
    void kickRelayRoomParticipantRejectsNonParticipantRequester() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID requesterUuid = createExistingUserWithNickname("배");
        UUID targetUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0),
            participant(targetUuid, "포도", false, 1)));

        performKick(requesterUuid, targetUuid).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("릴레이 방에 참여하지 않은 사용자입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * targetUserUuid 형식이 잘못되면 Redis 조회 없이 400을 반환합니다.
     */
    @Test
    void kickRelayRoomParticipantRejectsInvalidTargetUuid() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");

        performKick(hostUuid, "not-a-uuid").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 대상 UUID가 현재 참여자 목록에 없으면 명확한 대상 없음 메시지로 응답합니다.
     */
    @Test
    void kickRelayRoomParticipantReturnsNotFoundWhenTargetIsNotParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID targetUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0)));

        performKick(hostUuid, targetUuid).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("강퇴할 참여자를 찾을 수 없습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 방장은 자기 자신을 강퇴할 수 없습니다.
     */
    @Test
    void kickRelayRoomParticipantRejectsSelfKick() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0)));

        performKick(hostUuid, hostUuid).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("자기 자신은 강퇴할 수 없습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 강퇴 대상 참여자가 host로 표시되어 있으면 강퇴할 수 없습니다.
     */
    @Test
    void kickRelayRoomParticipantRejectsHostTarget() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID targetUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomStateWithHostUserUuid(hostUuid.toString(), RelayRoomStatus.WAITING,
            participant(hostUuid, "망고", true, 0), participant(targetUuid, "포도", true, 1)));

        performKick(hostUuid, targetUuid).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("방장은 강퇴할 수 없습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 게임 시작 이후 상태에서는 강퇴할 수 없습니다.
     */
    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"PLAYING", "FINALIZING", "FINISHED", "CLOSED"})
    void kickRelayRoomParticipantRejectsNonWaitingRoom(RelayRoomStatus roomStatus) throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID targetUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(roomStatus, participant(hostUuid, "망고", true, 0), participant(targetUuid, "포도", false, 1)));

        performKick(hostUuid, targetUuid).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("대기실에서만 강퇴할 수 있습니다."));

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

    private ResultActions performKick(UUID requesterUuid, Object targetUserUuid) throws Exception {
        return mockMvc.perform(
            post("/api/v1/relay/rooms/{roomCode}/kick", DEFAULT_ROOM_CODE).contentType(MediaType.APPLICATION_JSON)
                .header(ANONYMOUS_USER_UUID_HEADER, requesterUuid.toString()).content("""
                    {
                      "targetUserUuid": "%s"
                    }
                    """.formatted(targetUserUuid)));
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
        verify(valueOperations, atLeastOnce()).set(keyCaptor.capture(), jsonCaptor.capture(), eq(ROOM_STATE_TTL));

        String expectedKey = "relay:room:%s".formatted(DEFAULT_ROOM_CODE);
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

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }
}
