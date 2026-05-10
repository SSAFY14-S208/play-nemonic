package com.nemonicworld.flipbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFlipbookRoomRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisFlipbookRoomRepository repository;

    @BeforeEach
    void prepare() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = createValueOperationsMock();
        repository = new RedisFlipbookRoomRepository(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void findAllActiveRoomsScansRoomKeysAndFiltersClosedRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        FlipbookRoomState waitingRoom = roomState("FA2B3C", FlipbookRoomStatus.WAITING, null, null, null, now, host);
        FlipbookRoomState playingRoom = roomState("FB3K9Q", FlipbookRoomStatus.PLAYING, 3, 8, now.plusMinutes(1), now,
            host);
        FlipbookRoomState finishedRoom = roomState("FC4M8N", FlipbookRoomStatus.FINISHED, 8, 8, now.plusMinutes(2), now,
            host);
        FlipbookRoomState closedRoom = roomState("FZ9Y8X", FlipbookRoomStatus.CLOSED, 8, 8, now.plusMinutes(3), now,
            host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, true, false);
        given(cursor.next()).willReturn("flipbook:room:FA2B3C", "flipbook:room:FB3K9Q", "flipbook:room:FC4M8N",
            "flipbook:room:FZ9Y8X");
        given(valueOperations.get("flipbook:room:FA2B3C")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("flipbook:room:FB3K9Q")).willReturn(serialize(playingRoom));
        given(valueOperations.get("flipbook:room:FC4M8N")).willReturn(serialize(finishedRoom));
        given(valueOperations.get("flipbook:room:FZ9Y8X")).willReturn(serialize(closedRoom));

        List<FlipbookRoomState> activeRooms = repository.findAllActiveRooms();

        assertThat(activeRooms).containsExactly(waitingRoom, playingRoom, finishedRoom);
        verify(cursor).close();
    }

    private FlipbookRoomState roomState(String roomCode, FlipbookRoomStatus status, Integer currentRound,
        Integer totalRounds, LocalDateTime gameStartedAt, LocalDateTime createdAt,
        FlipbookRoomParticipant... participants) {
        LocalDateTime roundStartedAt = currentRound == null ? null : gameStartedAt;
        LocalDateTime roundDeadlineAt = roundStartedAt == null ? null : roundStartedAt.plusSeconds(45);

        return new FlipbookRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, currentRound, totalRounds,
            roundStartedAt, roundDeadlineAt, gameStartedAt, List.of(), List.of(participants), createdAt,
            createdAt.plusSeconds(1), List.of());
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private String serialize(FlipbookRoomState roomState) throws Exception {
        return objectMapper.writeValueAsString(roomState);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> createCursorMock() {
        return (Cursor<String>) mock(Cursor.class);
    }
}
