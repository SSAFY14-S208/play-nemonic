package com.nemonicworld.flipbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class RedisFlipbookRoomRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private RedisFlipbookRoomRepository repository;

    @BeforeEach
    void prepare() {
        redisTemplate = mock(StringRedisTemplate.class);
        redisOperations = createRedisOperationsMock();
        valueOperations = createValueOperationsMock();
        zSetOperations = createZSetOperationsMock();
        repository = new RedisFlipbookRoomRepository(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(redisOperations.opsForValue()).willReturn(valueOperations);
        given(redisOperations.opsForZSet()).willReturn(zSetOperations);
        given(redisOperations.exec()).willReturn(List.of("OK"));
        given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);

            return callback.execute(redisOperations);
        });
    }

    @Test
    void saveStoresActiveRoomIndexByStatus() {
        FlipbookRoomState roomState = roomState("FA2B3C", FlipbookRoomStatus.WAITING, null, null, null,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), participant(UUID.randomUUID(), "Mango", true, 0));

        repository.save(roomState);

        verify(valueOperations).set(eq("flipbook:room:FA2B3C"), any(String.class),
            eq(FlipbookRoomRepository.ROOM_STATE_TTL));
        verify(zSetOperations).add(eq("flipbook:rooms:active:created-at:WAITING"), eq("FA2B3C"), any(Double.class));
        verify(zSetOperations).add(eq("flipbook:rooms:active:expires-at:WAITING"), eq("FA2B3C"), any(Double.class));
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
    void findActiveRoomsByStatusesReadsStatusIndexesWithoutScanningRoomKeys() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        FlipbookRoomState waitingRoom = roomState("FA2B3C", FlipbookRoomStatus.WAITING, null, null, null, now, host);
        FlipbookRoomState playingRoom = roomState("FB3K9Q", FlipbookRoomStatus.PLAYING, 3, 8, now.plusMinutes(1), now,
            host);
        given(redisTemplate.hasKey("flipbook:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.reverseRange("flipbook:rooms:active:created-at:WAITING", 0, -1))
            .willReturn(new LinkedHashSet<>(List.of("FA2B3C")));
        given(zSetOperations.reverseRange("flipbook:rooms:active:created-at:PLAYING", 0, -1))
            .willReturn(new LinkedHashSet<>(List.of("FB3K9Q")));
        given(valueOperations.get("flipbook:room:FA2B3C")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("flipbook:room:FB3K9Q")).willReturn(serialize(playingRoom));

        List<FlipbookRoomState> activeRooms = repository
            .findActiveRoomsByStatuses(Set.of(FlipbookRoomStatus.WAITING, FlipbookRoomStatus.PLAYING));

        assertThat(activeRooms).containsExactlyInAnyOrder(waitingRoom, playingRoom);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void findActiveRoomsByStatusesWithPageReadsOnlyRequestedWindow() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        FlipbookRoomState roomA = roomState("ROOM_A", FlipbookRoomStatus.WAITING, null, null, null, now, host);
        FlipbookRoomState roomB = roomState("ROOM_B", FlipbookRoomStatus.WAITING, null, null, null, now.minusSeconds(1),
            host);
        FlipbookRoomState roomC = roomState("ROOM_C", FlipbookRoomStatus.WAITING, null, null, null, now.minusSeconds(2),
            host);
        FlipbookRoomState roomD = roomState("ROOM_D", FlipbookRoomStatus.WAITING, null, null, null, now.minusSeconds(3),
            host);
        given(redisTemplate.hasKey("flipbook:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.reverseRange("flipbook:rooms:active:created-at:WAITING", 0, 3))
            .willReturn(new LinkedHashSet<>(List.of("ROOM_A", "ROOM_B", "ROOM_C", "ROOM_D")));
        given(zSetOperations.zCard("flipbook:rooms:active:created-at:WAITING")).willReturn(5L);
        given(valueOperations.get("flipbook:room:ROOM_A")).willReturn(serialize(roomA));
        given(valueOperations.get("flipbook:room:ROOM_B")).willReturn(serialize(roomB));
        given(valueOperations.get("flipbook:room:ROOM_C")).willReturn(serialize(roomC));
        given(valueOperations.get("flipbook:room:ROOM_D")).willReturn(serialize(roomD));

        FlipbookActiveRoomPage page = repository.findActiveRoomsByStatuses(Set.of(FlipbookRoomStatus.WAITING), 1, 2);

        assertThat(page.items()).containsExactly(roomC, roomD);
        assertThat(page.totalElements()).isEqualTo(5L);
        verify(zSetOperations).reverseRange("flipbook:rooms:active:created-at:WAITING", 0, 3);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void countActiveRoomsByStatusesUsesStatusIndexesWhenInitialized() {
        given(redisTemplate.hasKey("flipbook:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.zCard("flipbook:rooms:active:created-at:WAITING")).willReturn(3L);
        given(zSetOperations.zCard("flipbook:rooms:active:created-at:PLAYING")).willReturn(2L);

        long activeRoomCount = repository
            .countActiveRoomsByStatuses(Set.of(FlipbookRoomStatus.WAITING, FlipbookRoomStatus.PLAYING));

        assertThat(activeRoomCount).isEqualTo(5L);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void countActiveRoomsByStatusesRemovesExpiredIndexMembersBeforeCounting() {
        given(redisTemplate.hasKey("flipbook:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.rangeByScore(eq("flipbook:rooms:active:expires-at:WAITING"), eq(0.0), anyDouble()))
            .willReturn(Set.of("STALE1"));
        given(zSetOperations.zCard("flipbook:rooms:active:created-at:WAITING")).willReturn(2L);

        long activeRoomCount = repository.countActiveRoomsByStatuses(Set.of(FlipbookRoomStatus.WAITING));

        assertThat(activeRoomCount).isEqualTo(2L);
        verify(zSetOperations).remove("flipbook:rooms:active:created-at:WAITING", "STALE1");
        verify(zSetOperations).remove("flipbook:rooms:active:expires-at:WAITING", "STALE1");
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void findActiveRoomsByStatusesFallsBackToScanAndBackfillsIndexesWhenIndexesAreEmpty() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        FlipbookRoomState waitingRoom = roomState("FA2B3C", FlipbookRoomStatus.WAITING, null, null, null, now, host);
        FlipbookRoomState finishedRoom = roomState("FC4M8N", FlipbookRoomStatus.FINISHED, 8, 8, now.plusMinutes(2), now,
            host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("flipbook:room:FA2B3C", "flipbook:room:FC4M8N");
        given(valueOperations.get("flipbook:room:FA2B3C")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("flipbook:room:FC4M8N")).willReturn(serialize(finishedRoom));

        List<FlipbookRoomState> activeRooms = repository.findActiveRoomsByStatuses(Set.of(FlipbookRoomStatus.WAITING));

        assertThat(activeRooms).containsExactly(waitingRoom);
        verify(zSetOperations).add(eq("flipbook:rooms:active:created-at:WAITING"), eq("FA2B3C"), any(Double.class));
        verify(zSetOperations).add(eq("flipbook:rooms:active:expires-at:WAITING"), eq("FA2B3C"), any(Double.class));
        verify(zSetOperations).add(eq("flipbook:rooms:active:created-at:FINISHED"), eq("FC4M8N"), any(Double.class));
        verify(zSetOperations).add(eq("flipbook:rooms:active:expires-at:FINISHED"), eq("FC4M8N"), any(Double.class));
        verify(valueOperations).set(eq("flipbook:rooms:active:index-initialized"), eq("true"),
            eq(FlipbookRoomRepository.ROOM_STATE_TTL));
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
    void findAbandonedWaitingRoomsScansAllDisconnectedOldWaitingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant disconnectedHost = disconnectedParticipant(hostUuid, "Mango", true, 0,
            now.minusMinutes(6));
        FlipbookRoomState abandonedRoom = roomState("WAITID", FlipbookRoomStatus.WAITING, null, null, null,
            now.minusMinutes(10), disconnectedHost);
        FlipbookRoomState connectedRoom = roomState("ACTIVE", FlipbookRoomStatus.WAITING, null, null, null,
            now.minusMinutes(10), participant(UUID.randomUUID(), "Peach", true, 0));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("flipbook:room:WAITID", "flipbook:room:ACTIVE");
        given(valueOperations.get("flipbook:room:WAITID")).willReturn(serialize(abandonedRoom));
        given(valueOperations.get("flipbook:room:ACTIVE")).willReturn(serialize(connectedRoom));

        List<FlipbookRoomState> abandonedRooms = repository.findAbandonedWaitingRooms(cutoff, 10);

        assertThat(abandonedRooms).containsExactly(abandonedRoom);
        verify(cursor).close();
    }

    @Test
    void findAbandonedPlayingRoomsScansAllDisconnectedOrDroppedOldPlayingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomParticipant disconnectedHost = disconnectedParticipant(hostUuid, "Mango", true, 0,
            now.minusMinutes(6));
        FlipbookRoomParticipant droppedParticipant = droppedParticipant(UUID.randomUUID(), "Dropped", false, 1,
            now.minusMinutes(7), now.minusMinutes(6));
        FlipbookRoomState abandonedRoom = roomState("PLAYID", FlipbookRoomStatus.PLAYING, 1, 4, now.minusMinutes(7),
            now.minusMinutes(7), disconnectedHost, droppedParticipant);
        FlipbookRoomState activeRoom = roomState("ACTIVE", FlipbookRoomStatus.PLAYING, 1, 4, now.minusMinutes(7),
            now.minusMinutes(7), participant(UUID.randomUUID(), "Active", true, 0));
        FlipbookRoomState recentRoom = roomState("RECENT", FlipbookRoomStatus.PLAYING, 1, 4, now.minusMinutes(7),
            now.minusMinutes(7), disconnectedParticipant(UUID.randomUUID(), "Recent", true, 0, now.minusMinutes(4)));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("flipbook:room:PLAYID", "flipbook:room:ACTIVE", "flipbook:room:RECENT");
        given(valueOperations.get("flipbook:room:PLAYID")).willReturn(serialize(abandonedRoom));
        given(valueOperations.get("flipbook:room:ACTIVE")).willReturn(serialize(activeRoom));
        given(valueOperations.get("flipbook:room:RECENT")).willReturn(serialize(recentRoom));

        List<FlipbookRoomState> abandonedRooms = repository.findAbandonedPlayingRooms(cutoff, 10);

        assertThat(abandonedRooms).containsExactly(abandonedRoom);
        verify(cursor).close();
    }

    @Test
    void findEmptyWaitingRoomsScansOnlyWaitingRoomsWithoutParticipants() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState emptyWaitingRoom = new FlipbookRoomState("EMPTY1", FlipbookRoomStatus.WAITING, null, 45, 2, 6,
            List.of(), now.minusMinutes(10), now.minusMinutes(6), List.of());
        FlipbookRoomState nonEmptyWaitingRoom = roomState("WAIT02", FlipbookRoomStatus.WAITING, null, null, null,
            now.minusMinutes(10), participant(UUID.randomUUID(), "Mango", true, 0));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("flipbook:room:EMPTY1", "flipbook:room:WAIT02");
        given(valueOperations.get("flipbook:room:EMPTY1")).willReturn(serialize(emptyWaitingRoom));
        given(valueOperations.get("flipbook:room:WAIT02")).willReturn(serialize(nonEmptyWaitingRoom));

        List<FlipbookRoomState> emptyRooms = repository.findEmptyWaitingRooms(10);

        assertThat(emptyRooms).containsExactly(emptyWaitingRoom);
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

    private FlipbookRoomParticipant disconnectedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
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
    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> createZSetOperationsMock() {
        return (ZSetOperations<String, String>) mock(ZSetOperations.class);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> createCursorMock() {
        return (Cursor<String>) mock(Cursor.class);
    }
}
