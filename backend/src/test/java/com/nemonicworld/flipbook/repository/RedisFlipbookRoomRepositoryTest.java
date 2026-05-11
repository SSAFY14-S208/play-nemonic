package com.nemonicworld.flipbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
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

    @Test
    void findPlayingRoomsForDisconnectGraceIncludesDroppedParticipantPendingCurrentAssignment() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant droppedHost = droppedParticipant(hostUuid, "Mango", true, 0, now.minusSeconds(20),
            now.minusSeconds(10));
        FlipbookFrameAssignment pendingAssignment = pendingAssignment(0, 0, 1, hostUuid);
        FlipbookRoomState roomState = roomState("FB3K9Q", FlipbookRoomStatus.PLAYING, 1, 4, now.minusSeconds(45),
            now.minusMinutes(1), List.of(pendingAssignment), droppedHost);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, false);
        given(cursor.next()).willReturn("flipbook:room:FB3K9Q");
        given(valueOperations.get("flipbook:room:FB3K9Q")).willReturn(serialize(roomState));

        List<FlipbookRoomState> rooms = repository.findPlayingRoomsForDisconnectGrace(now.minusSeconds(10), 100);

        assertThat(rooms).containsExactly(roomState);
        verify(cursor).close();
    }

    @Test
    void findClosableFinishedRoomsScansRoomKeysAndFiltersOldFinishedRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime closeCutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        FlipbookRoomState oldFinishedRoom = roomState("CLOSE1", FlipbookRoomStatus.FINISHED, 8, 8, now.minusMinutes(10),
            now.minusMinutes(9), host).finish(now.minusMinutes(6));
        FlipbookRoomState recentFinishedRoom = roomState("RECENT", FlipbookRoomStatus.FINISHED, 8, 8,
            now.minusMinutes(10), now.minusMinutes(9), host).finish(now.minusMinutes(1));
        FlipbookRoomState finalizingRoom = roomState("FINAL1", FlipbookRoomStatus.FINALIZING, 8, 8,
            now.minusMinutes(10), now.minusMinutes(9), host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("flipbook:room:CLOSE1", "flipbook:room:RECENT", "flipbook:room:FINAL1");
        given(valueOperations.get("flipbook:room:CLOSE1")).willReturn(serialize(oldFinishedRoom));
        given(valueOperations.get("flipbook:room:RECENT")).willReturn(serialize(recentFinishedRoom));
        given(valueOperations.get("flipbook:room:FINAL1")).willReturn(serialize(finalizingRoom));

        List<FlipbookRoomState> closableRooms = repository.findClosableFinishedRooms(closeCutoff, 10);

        assertThat(closableRooms).containsExactly(oldFinishedRoom);
        verify(cursor).close();
    }

    private FlipbookRoomState roomState(String roomCode, FlipbookRoomStatus status, Integer currentRound,
        Integer totalRounds, LocalDateTime gameStartedAt, LocalDateTime createdAt,
        FlipbookRoomParticipant... participants) {
        return roomState(roomCode, status, currentRound, totalRounds, gameStartedAt, createdAt, List.of(),
            participants);
    }

    private FlipbookRoomState roomState(String roomCode, FlipbookRoomStatus status, Integer currentRound,
        Integer totalRounds, LocalDateTime gameStartedAt, LocalDateTime createdAt,
        List<FlipbookFrameAssignment> assignments, FlipbookRoomParticipant... participants) {
        LocalDateTime roundStartedAt = currentRound == null ? null : gameStartedAt;
        LocalDateTime roundDeadlineAt = roundStartedAt == null ? null : roundStartedAt.plusSeconds(45);

        return new FlipbookRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, currentRound, totalRounds,
            roundStartedAt, roundDeadlineAt, gameStartedAt, assignments, List.of(participants), createdAt,
            createdAt.plusSeconds(1), List.of());
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private FlipbookRoomParticipant droppedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS), true, droppedAt);
    }

    private FlipbookFrameAssignment pendingAssignment(int flipbookIndex, int frameIndex, int round,
        UUID assignedUserUuid) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, round, assignedUserUuid.toString(),
            FlipbookFrameAssignmentStatus.PENDING, null, null, false, false, null);
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
