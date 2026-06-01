package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

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
    private ZSetOperations<String, String> zSetOperations;
    private RedisRelayRoomRepository repository;

    @BeforeEach
    void prepare() {
        redisTemplate = mock(StringRedisTemplate.class);
        redisOperations = createRedisOperationsMock();
        valueOperations = createValueOperationsMock();
        zSetOperations = createZSetOperationsMock();
        repository = new RedisRelayRoomRepository(redisTemplate, objectMapper);

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
        RelayRoomState roomState = roomState(participant(UUID.randomUUID(), "망고", true, 0));

        repository.save(roomState);

        verify(valueOperations).set(eq(ROOM_KEY), any(String.class), eq(ROOM_STATE_TTL));
        verify(zSetOperations).add(eq("relay:rooms:active:created-at:WAITING"), eq(ROOM_CODE), any(Double.class));
        verify(zSetOperations).add(eq("relay:rooms:active:expires-at:WAITING"), eq(ROOM_CODE), any(Double.class));
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
        verify(zSetOperations).add(eq("relay:rooms:active:created-at:WAITING"), eq(ROOM_CODE), any(Double.class));
        verify(zSetOperations).add(eq("relay:rooms:active:expires-at:WAITING"), eq(ROOM_CODE), any(Double.class));
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
        assertThat(roomState.kickedUserUuids()).isEmpty();
        assertThat(roomState.participantCount()).isEqualTo(1);
    }

    @Test
    void findExpiredPlayingRoomsScansRoomKeysAndFiltersExpiredPlayingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        RelayRoomState expiredPlayingRoom = roomState("EXPIRED", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusSeconds(45), now, host);
        RelayRoomState futurePlayingRoom = roomState("FUTURE1", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusSeconds(10), now.plusSeconds(30), host);
        RelayRoomState waitingRoom = roomState("WAIT01", RelayRoomStatus.WAITING, null, null, null, host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("relay:room:EXPIRED", "relay:room:FUTURE1", "relay:room:WAIT01");
        given(valueOperations.get("relay:room:EXPIRED")).willReturn(serialize(expiredPlayingRoom));
        given(valueOperations.get("relay:room:FUTURE1")).willReturn(serialize(futurePlayingRoom));
        given(valueOperations.get("relay:room:WAIT01")).willReturn(serialize(waitingRoom));

        List<RelayRoomState> expiredRooms = repository.findExpiredPlayingRooms(now, 10);

        assertThat(expiredRooms).containsExactly(expiredPlayingRoom);
        verify(cursor).close();
    }

    @Test
    void findFinalizingRoomsScansRoomKeysAndFiltersFinalizingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        RelayRoomState finalizingRoom = roomState("FINAL1", RelayRoomStatus.FINALIZING, RelayDrawingPart.LEGS,
            now.minusSeconds(45), now, host);
        RelayRoomState playingRoom = roomState("PLAY01", RelayRoomStatus.PLAYING, RelayDrawingPart.LEGS,
            now.minusSeconds(45), now, host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("relay:room:FINAL1", "relay:room:PLAY01");
        given(valueOperations.get("relay:room:FINAL1")).willReturn(serialize(finalizingRoom));
        given(valueOperations.get("relay:room:PLAY01")).willReturn(serialize(playingRoom));

        List<RelayRoomState> finalizingRooms = repository.findFinalizingRooms(10);

        assertThat(finalizingRooms).containsExactly(finalizingRoom);
        verify(cursor).close();
    }

    @Test
    void findAbandonedWaitingRoomsScansAllDisconnectedOldWaitingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant oldDisconnectedHost = disconnectedParticipant(hostUuid, "Mango", true, 0,
            now.minusMinutes(6));
        RelayRoomState abandonedRoom = roomState("WAITID", RelayRoomStatus.WAITING, null, null, null,
            now.minusMinutes(6), oldDisconnectedHost);
        RelayRoomState recentRoom = roomState("RECENT", RelayRoomStatus.WAITING, null, null, null, now.minusMinutes(4),
            disconnectedParticipant(UUID.randomUUID(), "Recent", true, 0, now.minusMinutes(4)));
        RelayRoomState connectedRoom = roomState("ACTIVE", RelayRoomStatus.WAITING, null, null, null,
            now.minusMinutes(10), participant(UUID.randomUUID(), "Active", true, 0));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("relay:room:WAITID", "relay:room:RECENT", "relay:room:ACTIVE");
        given(valueOperations.get("relay:room:WAITID")).willReturn(serialize(abandonedRoom));
        given(valueOperations.get("relay:room:RECENT")).willReturn(serialize(recentRoom));
        given(valueOperations.get("relay:room:ACTIVE")).willReturn(serialize(connectedRoom));

        List<RelayRoomState> abandonedRooms = repository.findAbandonedWaitingRooms(cutoff, 10);

        assertThat(abandonedRooms).containsExactly(abandonedRoom);
        verify(cursor).close();
    }

    @Test
    void findAbandonedPlayingRoomsScansAllDisconnectedOrDroppedOldPlayingRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant disconnectedHost = disconnectedParticipant(hostUuid, "Mango", true, 0,
            now.minusMinutes(6));
        RelayRoomParticipant droppedParticipant = droppedParticipant(UUID.randomUUID(), "Dropped", false, 1,
            now.minusMinutes(7), now.minusMinutes(6));
        RelayRoomState abandonedRoom = roomState("PLAYID", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusMinutes(7), now.minusMinutes(6), now.minusMinutes(6), disconnectedHost, droppedParticipant);
        RelayRoomState activeRoom = roomState("ACTIVE", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusMinutes(7), now.minusMinutes(6), now.minusMinutes(6),
            participant(UUID.randomUUID(), "Active", true, 0));
        RelayRoomState recentRoom = roomState("RECENT", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusMinutes(7), now.minusMinutes(6), now.minusMinutes(4),
            disconnectedParticipant(UUID.randomUUID(), "Recent", true, 0, now.minusMinutes(4)));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("relay:room:PLAYID", "relay:room:ACTIVE", "relay:room:RECENT");
        given(valueOperations.get("relay:room:PLAYID")).willReturn(serialize(abandonedRoom));
        given(valueOperations.get("relay:room:ACTIVE")).willReturn(serialize(activeRoom));
        given(valueOperations.get("relay:room:RECENT")).willReturn(serialize(recentRoom));

        List<RelayRoomState> abandonedRooms = repository.findAbandonedPlayingRooms(cutoff, 10);

        assertThat(abandonedRooms).containsExactly(abandonedRoom);
        verify(cursor).close();
    }

    @Test
    void findRoomsForConnectionReconciliationScansWaitingAndPlayingRoomsWithConnectedParticipants() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant connectedHost = participant(hostUuid, "Mango", true, 0);
        RelayRoomState waitingRoom = roomState("WAIT01", RelayRoomStatus.WAITING, null, null, null, connectedHost);
        RelayRoomState playingRoom = roomState("PLAY01", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusSeconds(45), now, connectedHost);
        RelayRoomState disconnectedWaitingRoom = roomState("WAIT02", RelayRoomStatus.WAITING, null, null, null,
            disconnectedParticipant(UUID.randomUUID(), "Disconnected", true, 0, now.minusMinutes(1)));
        RelayRoomState finalizingRoom = roomState("FINAL1", RelayRoomStatus.FINALIZING, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), connectedHost);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, true, false);
        given(cursor.next()).willReturn("relay:room:WAIT01", "relay:room:PLAY01", "relay:room:WAIT02",
            "relay:room:FINAL1");
        given(valueOperations.get("relay:room:WAIT01")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("relay:room:PLAY01")).willReturn(serialize(playingRoom));
        given(valueOperations.get("relay:room:WAIT02")).willReturn(serialize(disconnectedWaitingRoom));
        given(valueOperations.get("relay:room:FINAL1")).willReturn(serialize(finalizingRoom));

        List<RelayRoomState> candidateRooms = repository.findRoomsForConnectionReconciliation(10);

        assertThat(candidateRooms).containsExactly(waitingRoom, playingRoom);
        verify(cursor).close();
    }

    @Test
    void findEmptyWaitingRoomsScansOnlyWaitingRoomsWithoutParticipants() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        RelayRoomState emptyWaitingRoom = new RelayRoomState("EMPTY1", RelayRoomStatus.WAITING, null, 60, 2, 6, null,
            List.of(), List.of(), null, null, null, now.minusMinutes(10), now.minusMinutes(1));
        RelayRoomState waitingRoom = roomState("WAIT01", RelayRoomStatus.WAITING, null, null, null, host);
        RelayRoomState playingEmptyRoom = new RelayRoomState("PLAY01", RelayRoomStatus.PLAYING, null, 60, 2, 6,
            RelayDrawingPart.FACE, List.of(), List.of(), now.minusMinutes(2), now, now.minusMinutes(2),
            now.minusMinutes(10), now.minusMinutes(1));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("relay:room:EMPTY1", "relay:room:WAIT01", "relay:room:PLAY01");
        given(valueOperations.get("relay:room:EMPTY1")).willReturn(serialize(emptyWaitingRoom));
        given(valueOperations.get("relay:room:WAIT01")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("relay:room:PLAY01")).willReturn(serialize(playingEmptyRoom));

        List<RelayRoomState> emptyRooms = repository.findEmptyWaitingRooms(10);

        assertThat(emptyRooms).containsExactly(emptyWaitingRoom);
        verify(cursor).close();
    }

    @Test
    void findClosableFinishedRoomsScansRoomKeysAndFiltersOldFinishedRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime closeCutoff = now.minusMinutes(5);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        RelayRoomState oldFinishedRoom = roomState("CLOSE1", RelayRoomStatus.FINISHED, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host).finish(now.minusMinutes(6));
        RelayRoomState recentFinishedRoom = roomState("RECENT", RelayRoomStatus.FINISHED, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host).finish(now.minusMinutes(1));
        RelayRoomState finalizingRoom = roomState("FINAL1", RelayRoomStatus.FINALIZING, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn("relay:room:CLOSE1", "relay:room:RECENT", "relay:room:FINAL1");
        given(valueOperations.get("relay:room:CLOSE1")).willReturn(serialize(oldFinishedRoom));
        given(valueOperations.get("relay:room:RECENT")).willReturn(serialize(recentFinishedRoom));
        given(valueOperations.get("relay:room:FINAL1")).willReturn(serialize(finalizingRoom));

        List<RelayRoomState> closableRooms = repository.findClosableFinishedRooms(closeCutoff, 10);

        assertThat(closableRooms).containsExactly(oldFinishedRoom);
        verify(cursor).close();
    }

    @Test
    void findClosedRoomsScansRoomKeysAndFiltersClosedRooms() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = participant(hostUuid, "Mango", true, 0);
        RelayRoomState closedRoom = roomState("CLOSED", RelayRoomStatus.CLOSED, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host);
        RelayRoomState finishedRoom = roomState("FINISH", RelayRoomStatus.FINISHED, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("relay:room:CLOSED", "relay:room:FINISH");
        given(valueOperations.get("relay:room:CLOSED")).willReturn(serialize(closedRoom));
        given(valueOperations.get("relay:room:FINISH")).willReturn(serialize(finishedRoom));

        List<RelayRoomState> closedRooms = repository.findClosedRooms(10);

        assertThat(closedRooms).containsExactly(closedRoom);
        verify(cursor).close();
    }

    @Test
    void findActiveRoomsByStatusesReadsStatusIndexesWithoutScanningRoomKeys() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        RelayRoomState waitingRoom = roomState("WAIT01", RelayRoomStatus.WAITING, null, null, null, now, host);
        RelayRoomState playingRoom = roomState("PLAY01", RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            now.minusSeconds(45), now, host);
        given(redisTemplate.hasKey("relay:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.reverseRange("relay:rooms:active:created-at:WAITING", 0, -1))
            .willReturn(new LinkedHashSet<>(List.of("WAIT01")));
        given(zSetOperations.reverseRange("relay:rooms:active:created-at:PLAYING", 0, -1))
            .willReturn(new LinkedHashSet<>(List.of("PLAY01")));
        given(valueOperations.get("relay:room:WAIT01")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("relay:room:PLAY01")).willReturn(serialize(playingRoom));

        List<RelayRoomState> activeRooms = repository
            .findActiveRoomsByStatuses(Set.of(RelayRoomStatus.WAITING, RelayRoomStatus.PLAYING));

        assertThat(activeRooms).containsExactlyInAnyOrder(waitingRoom, playingRoom);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void findActiveRoomsByStatusesWithPageReadsOnlyRequestedWindow() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        RelayRoomState roomA = roomState("ROOM_A", RelayRoomStatus.WAITING, null, null, null, now, host);
        RelayRoomState roomB = roomState("ROOM_B", RelayRoomStatus.WAITING, null, null, null, now.minusSeconds(1),
            host);
        RelayRoomState roomC = roomState("ROOM_C", RelayRoomStatus.WAITING, null, null, null, now.minusSeconds(2),
            host);
        RelayRoomState roomD = roomState("ROOM_D", RelayRoomStatus.WAITING, null, null, null, now.minusSeconds(3),
            host);
        given(redisTemplate.hasKey("relay:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.reverseRange("relay:rooms:active:created-at:WAITING", 0, 3))
            .willReturn(new LinkedHashSet<>(List.of("ROOM_A", "ROOM_B", "ROOM_C", "ROOM_D")));
        given(zSetOperations.zCard("relay:rooms:active:created-at:WAITING")).willReturn(5L);
        given(valueOperations.get("relay:room:ROOM_A")).willReturn(serialize(roomA));
        given(valueOperations.get("relay:room:ROOM_B")).willReturn(serialize(roomB));
        given(valueOperations.get("relay:room:ROOM_C")).willReturn(serialize(roomC));
        given(valueOperations.get("relay:room:ROOM_D")).willReturn(serialize(roomD));

        RelayActiveRoomPage page = repository.findActiveRoomsByStatuses(Set.of(RelayRoomStatus.WAITING), 1, 2);

        assertThat(page.items()).containsExactly(roomC, roomD);
        assertThat(page.totalElements()).isEqualTo(5L);
        verify(zSetOperations).reverseRange("relay:rooms:active:created-at:WAITING", 0, 3);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void countActiveRoomsByStatusesUsesStatusIndexesWhenInitialized() {
        given(redisTemplate.hasKey("relay:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.zCard("relay:rooms:active:created-at:WAITING")).willReturn(3L);
        given(zSetOperations.zCard("relay:rooms:active:created-at:PLAYING")).willReturn(2L);

        long activeRoomCount = repository
            .countActiveRoomsByStatuses(Set.of(RelayRoomStatus.WAITING, RelayRoomStatus.PLAYING));

        assertThat(activeRoomCount).isEqualTo(5L);
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void countActiveRoomsByStatusesRemovesExpiredIndexMembersBeforeCounting() {
        given(redisTemplate.hasKey("relay:rooms:active:index-initialized")).willReturn(true);
        given(zSetOperations.rangeByScore(eq("relay:rooms:active:expires-at:WAITING"), eq(0.0), anyDouble()))
            .willReturn(Set.of("STALE1"));
        given(zSetOperations.zCard("relay:rooms:active:created-at:WAITING")).willReturn(2L);

        long activeRoomCount = repository.countActiveRoomsByStatuses(Set.of(RelayRoomStatus.WAITING));

        assertThat(activeRoomCount).isEqualTo(2L);
        verify(zSetOperations).remove("relay:rooms:active:created-at:WAITING", "STALE1");
        verify(zSetOperations).remove("relay:rooms:active:expires-at:WAITING", "STALE1");
        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void findActiveRoomsByStatusesFallsBackToScanAndBackfillsIndexesWhenIndexesAreEmpty() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant host = participant(UUID.randomUUID(), "Mango", true, 0);
        RelayRoomState waitingRoom = roomState("WAIT01", RelayRoomStatus.WAITING, null, null, null, now, host);
        RelayRoomState finishedRoom = roomState("FINISH", RelayRoomStatus.FINISHED, RelayDrawingPart.LEGS,
            now.minusMinutes(10), now.minusMinutes(9), host);
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("relay:room:WAIT01", "relay:room:FINISH");
        given(valueOperations.get("relay:room:WAIT01")).willReturn(serialize(waitingRoom));
        given(valueOperations.get("relay:room:FINISH")).willReturn(serialize(finishedRoom));

        List<RelayRoomState> activeRooms = repository.findActiveRoomsByStatuses(Set.of(RelayRoomStatus.WAITING));

        assertThat(activeRooms).containsExactly(waitingRoom);
        verify(zSetOperations).add(eq("relay:rooms:active:created-at:WAITING"), eq("WAIT01"), any(Double.class));
        verify(zSetOperations).add(eq("relay:rooms:active:expires-at:WAITING"), eq("WAIT01"), any(Double.class));
        verify(zSetOperations).add(eq("relay:rooms:active:created-at:FINISHED"), eq("FINISH"), any(Double.class));
        verify(zSetOperations).add(eq("relay:rooms:active:expires-at:FINISHED"), eq("FINISH"), any(Double.class));
        verify(valueOperations).set(eq("relay:rooms:active:index-initialized"), eq("true"), eq(ROOM_STATE_TTL));
        verify(cursor).close();
    }

    @Test
    void acquireAndReleaseFinalizationLockUsesSeparateLockKey() {
        Duration lockTtl = Duration.ofSeconds(60);
        String lockToken = "token-1";
        given(valueOperations.setIfAbsent("relay:room-finalization-lock:" + ROOM_CODE, lockToken, lockTtl))
            .willReturn(true);

        boolean acquired = repository.acquireFinalizationLock(ROOM_CODE, lockToken, lockTtl);
        repository.releaseFinalizationLock(ROOM_CODE, lockToken);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("relay:room-finalization-lock:" + ROOM_CODE, lockToken, lockTtl);
        verify(redisTemplate).execute(any(), eq(List.of("relay:room-finalization-lock:" + ROOM_CODE)), eq(lockToken));
    }

    @Test
    void tempCleanupMarkerUsesSeparateMarkerKey() {
        LocalDateTime cleanedAt = LocalDateTime.of(2026, 5, 6, 16, 0);
        Duration markerTtl = Duration.ofHours(24);
        given(redisTemplate.hasKey("relay:room-temp-cleanup:" + ROOM_CODE)).willReturn(true);

        boolean marked = repository.isTempCleanupMarked(ROOM_CODE);
        repository.markTempCleanup(ROOM_CODE, cleanedAt, markerTtl);

        assertThat(marked).isTrue();
        verify(redisTemplate).hasKey("relay:room-temp-cleanup:" + ROOM_CODE);
        verify(valueOperations).set("relay:room-temp-cleanup:" + ROOM_CODE, cleanedAt.toString(), markerTtl);
    }

    @Test
    void acquireAndReleaseTempCleanupLockUsesSeparateLockKey() {
        Duration lockTtl = Duration.ofSeconds(60);
        given(valueOperations.setIfAbsent("relay:room-temp-cleanup-lock:" + ROOM_CODE, "locked", lockTtl))
            .willReturn(true);

        boolean acquired = repository.acquireTempCleanupLock(ROOM_CODE, lockTtl);
        repository.releaseTempCleanupLock(ROOM_CODE);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("relay:room-temp-cleanup-lock:" + ROOM_CODE, "locked", lockTtl);
        verify(redisTemplate).delete("relay:room-temp-cleanup-lock:" + ROOM_CODE);
    }

    private RelayRoomState roomState(RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.WAITING, participants[0].userUuid(), 60, 2, 6, null,
            List.of(participants), createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomState roomState(String roomCode, RelayRoomStatus status, RelayDrawingPart currentPart,
        LocalDateTime partStartedAt, LocalDateTime partDeadlineAt, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(roomCode, status, participants[0].userUuid(), 60, 2, 6, currentPart,
            List.of(participants), List.of(), partStartedAt, partDeadlineAt, partStartedAt, createdAt,
            createdAt.plusSeconds(1));
    }

    private RelayRoomState roomState(String roomCode, RelayRoomStatus status, RelayDrawingPart currentPart,
        LocalDateTime partStartedAt, LocalDateTime partDeadlineAt, LocalDateTime updatedAt,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = updatedAt.minusMinutes(1);

        return new RelayRoomState(roomCode, status, participants[0].userUuid(), 60, 2, 6, currentPart,
            List.of(participants), List.of(), partStartedAt, partDeadlineAt, partStartedAt, createdAt, updatedAt);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private RelayRoomParticipant disconnectedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
            disconnectedAt.minusMinutes(1));
    }

    private RelayRoomParticipant droppedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
            disconnectedAt.minusMinutes(1), true, droppedAt);
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

    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> createZSetOperationsMock() {
        return (ZSetOperations<String, String>) mock(ZSetOperations.class);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> createCursorMock() {
        return (Cursor<String>) mock(Cursor.class);
    }
}
