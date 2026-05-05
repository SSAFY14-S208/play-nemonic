package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis WATCH 기반 조건부 저장이 기대한 현재 상태에만 성공하는지 검증합니다.
 */
class RedisRelayRoomRepositoryTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String ROOM_KEY = "relay:room:" + ROOM_CODE;
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private RedisRelayRoomRepository repository;

    @BeforeEach
    void prepare() {
        redisTemplate = mock(StringRedisTemplate.class);
        redisOperations = createRedisOperationsMock();
        valueOperations = createValueOperationsMock();
        repository = new RedisRelayRoomRepository(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisOperations.opsForValue()).willReturn(valueOperations);
        given(redisOperations.exec()).willReturn(List.of("OK"));
        given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);

            return callback.execute(redisOperations);
        });
    }

    /**
     * WATCH 이후 다시 읽은 Redis 상태가 기대 상태와 같으면 transaction으로 새 JSON을 저장합니다.
     */
    @Test
    void saveIfUnchangedStoresUpdatedRoomWhenCurrentStateMatchesExpectedState() throws Exception {
        RelayRoomState expectedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0));
        RelayRoomState updatedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0),
            participant(UUID.randomUUID(), "포도", false, 1));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(expectedRoomState));

        boolean saved = repository.saveIfUnchanged(expectedRoomState, updatedRoomState);

        assertThat(saved).isTrue();
        verify(redisOperations).watch(ROOM_KEY);
        verify(redisOperations).multi();
        verify(redisOperations).exec();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(ROOM_KEY), jsonCaptor.capture(), eq(ROOM_STATE_TTL));
        assertThat(deserialize(jsonCaptor.getValue())).isEqualTo(updatedRoomState);
    }

    /**
     * WATCH 중 다른 요청이 먼저 저장하면 EXEC 결과가 null이 되므로 실패로 반환합니다.
     */
    @Test
    void saveIfUnchangedReturnsFalseWhenWatchedKeyChangesBeforeExec() throws Exception {
        RelayRoomState expectedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0));
        RelayRoomState updatedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0),
            participant(UUID.randomUUID(), "포도", false, 1));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(expectedRoomState));
        given(redisOperations.exec()).willReturn(null);

        boolean saved = repository.saveIfUnchanged(expectedRoomState, updatedRoomState);

        assertThat(saved).isFalse();
        verify(redisOperations).watch(ROOM_KEY);
        verify(redisOperations).multi();
        verify(valueOperations).set(eq(ROOM_KEY), any(String.class), eq(ROOM_STATE_TTL));
    }

    /**
     * WATCH 이후 읽은 현재 상태가 기대 상태와 다르면 저장하지 않고 watch를 해제합니다.
     */
    @Test
    void saveIfUnchangedReturnsFalseWithoutSavingWhenCurrentStateDiffersFromExpectedState() throws Exception {
        RelayRoomState expectedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0));
        RelayRoomState changedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0),
            participant(UUID.randomUUID(), "사과", false, 1));
        RelayRoomState updatedRoomState = roomState(participant(UUID.randomUUID(), "망고", true, 0),
            participant(UUID.randomUUID(), "포도", false, 1));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(changedRoomState));

        boolean saved = repository.saveIfUnchanged(expectedRoomState, updatedRoomState);

        assertThat(saved).isFalse();
        verify(redisOperations).watch(ROOM_KEY);
        verify(redisOperations).unwatch();
        verify(redisOperations, never()).multi();
        verify(valueOperations, never()).set(eq(ROOM_KEY), any(String.class), eq(ROOM_STATE_TTL));
    }

    /**
     * 기존 Redis JSON에 게임 진행 필드가 없더라도 assignments는 빈 리스트로 보정해 읽습니다.
     */
    @Test
    void findByRoomCodeReadsLegacyRoomJsonWithoutGameFields() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        given(valueOperations.get(ROOM_KEY)).willReturn("""
            {
              "roomCode": "%s",
              "status": "WAITING",
              "hostUserUuid": "%s",
              "timeLimitSeconds": 45,
              "minParticipants": 2,
              "maxParticipants": 6,
              "currentPart": null,
              "participants": [
                {
                  "userUuid": "%s",
                  "nickname": "Mango",
                  "host": true,
                  "joinOrder": 0,
                  "connected": true,
                  "disconnectedAt": null,
                  "joinedAt": "%s"
                }
              ],
              "createdAt": "%s",
              "updatedAt": "%s"
            }
            """.formatted(ROOM_CODE, hostUuid, hostUuid, now, now, now));

        RelayRoomState roomState = repository.findByRoomCode(ROOM_CODE).orElseThrow();

        assertThat(roomState.assignments()).isEmpty();
        assertThat(roomState.partStartedAt()).isNull();
        assertThat(roomState.partDeadlineAt()).isNull();
        assertThat(roomState.gameStartedAt()).isNull();
        assertThat(roomState.participantCount()).isEqualTo(1);
    }

    private RelayRoomState roomState(RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.WAITING, participants[0].userUuid(), 60, 2, 6, null,
            List.of(participants), createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private String serialize(RelayRoomState roomState) throws Exception {
        return objectMapper.writeValueAsString(roomState);
    }

    private RelayRoomState deserialize(String roomStateValue) throws Exception {
        return objectMapper.readValue(roomStateValue, RelayRoomState.class);
    }

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
