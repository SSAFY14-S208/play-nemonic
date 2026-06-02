package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 릴레이 방 상태를 Redis 문자열 JSON 값으로 저장하고 조회하는 저장소입니다.
 */
@Repository
public class RedisRelayRoomRepository implements RelayRoomRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisRelayRoomRepository.class);

    private static final String ROOM_KEY_PREFIX = "relay:room:";
    private static final String ACTIVE_ROOM_INDEX_KEY_PREFIX = "relay:rooms:active:created-at:";
    private static final String ACTIVE_ROOM_EXPIRY_INDEX_KEY_PREFIX = "relay:rooms:active:expires-at:";
    private static final String ACTIVE_ROOM_INDEX_INITIALIZED_KEY = "relay:rooms:active:index-initialized";
    private static final String FINALIZATION_LOCK_KEY_PREFIX = "relay:room-finalization-lock:";
    private static final String TEMP_CLEANUP_MARKER_KEY_PREFIX = "relay:room-temp-cleanup:";
    private static final String TEMP_CLEANUP_LOCK_KEY_PREFIX = "relay:room-temp-cleanup-lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "릴레이 방 상태를 저장할 수 없습니다.";
    private static final String ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE = "릴레이 방 상태를 읽을 수 없습니다.";
    private static final Set<RelayRoomStatus> NON_CLOSED_STATUSES = EnumSet.of(RelayRoomStatus.WAITING,
        RelayRoomStatus.PLAYING, RelayRoomStatus.FINALIZING, RelayRoomStatus.FINISHED);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRelayRoomRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 방코드 충돌 방지를 위해 Redis key 존재 여부를 확인합니다.
     */
    @Override
    public boolean existsByRoomCode(String roomCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createRoomKey(roomCode)));
    }

    /**
     * 진행 중 방 상태를 임시 이미지 fallback 정리 기준과 같은 24시간 TTL로 저장합니다.
     */
    @Override
    public void save(RelayRoomState roomState) {
        redisTemplate.opsForValue().set(createRoomKey(roomState.roomCode()), serialize(roomState),
            RelayRoomRepository.ROOM_STATE_TTL);
        syncActiveRoomIndex(roomState);
    }

    /**
     * WATCH/MULTI/EXEC를 사용해 기대한 Redis 상태가 유지될 때만 새 상태를 저장합니다.
     */
    @Override
    public boolean saveIfUnchanged(RelayRoomState expectedRoomState, RelayRoomState updatedRoomState) {
        String roomKey = createRoomKey(expectedRoomState.roomCode());

        Boolean updated = redisTemplate.execute(new SessionCallback<>() {

            @Override
            public <K, V> Boolean execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> stringOperations = castToStringOperations(operations);

                stringOperations.watch(roomKey);
                String currentRoomStateValue = stringOperations.opsForValue().get(roomKey);

                if (!StringUtils.hasText(currentRoomStateValue)) {
                    stringOperations.unwatch();
                    return false;
                }

                RelayRoomState currentRoomState = deserialize(currentRoomStateValue);

                if (!currentRoomState.equals(expectedRoomState)) {
                    stringOperations.unwatch();
                    return false;
                }

                stringOperations.multi();
                stringOperations.opsForValue().set(roomKey, serialize(updatedRoomState),
                    RelayRoomRepository.ROOM_STATE_TTL);
                syncActiveRoomIndex(stringOperations, updatedRoomState);
                List<Object> results = stringOperations.exec();

                return results != null;
            }
        });

        return Boolean.TRUE.equals(updated);
    }

    /**
     * Redis에 저장된 JSON을 방 상태 모델로 역직렬화합니다.
     */
    @Override
    public Optional<RelayRoomState> findByRoomCode(String roomCode) {
        String roomStateValue = redisTemplate.opsForValue().get(createRoomKey(roomCode));

        // Redis key가 없거나 비어 있으면 만료되었거나 존재하지 않는 방으로 간주합니다.
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        // JSON 파싱 실패는 클라이언트 입력 문제가 아니라 저장 데이터 문제이므로 내부 오류로 올립니다.
        return Optional.of(deserialize(roomStateValue));
    }

    /**
     * SCAN으로 릴레이 방 key를 순회하며 마감된 PLAYING 방만 조회합니다.
     */
    @Override
    public List<RelayRoomState> findExpiredPlayingRooms(LocalDateTime now, int limit) {
        return scanRoomKeys("expired_playing", limit, limit, roomKey -> findExpiredPlayingRoom(roomKey, now));
    }

    /**
     * Redis room key를 SCAN하며 이탈 확정 또는 이탈자 현재 배정 자동 제출이 필요한 PLAYING 방만 조회합니다.
     */
    @Override
    public List<RelayRoomState> findPlayingRoomsForDisconnectGrace(LocalDateTime disconnectCutoff, int limit) {
        return scanRoomKeys("disconnect_grace", limit, limit,
            roomKey -> findPlayingRoomForDisconnectGrace(roomKey, disconnectCutoff));
    }

    @Override
    public List<RelayRoomState> findAbandonedWaitingRooms(LocalDateTime idleCutoff, int limit) {
        return scanRoomKeys("abandoned_waiting", limit, limit,
            roomKey -> findAbandonedWaitingRoom(roomKey, idleCutoff));
    }

    @Override
    public List<RelayRoomState> findAbandonedPlayingRooms(LocalDateTime abandonedCutoff, int limit) {
        return scanRoomKeys("abandoned_playing", limit, limit,
            roomKey -> findAbandonedPlayingRoom(roomKey, abandonedCutoff));
    }

    /**
     * Redis room key를 SCAN하며 실제 WebSocket 세션 보정이 필요한 WAITING/PLAYING 방만 골라냅니다.
     */
    @Override
    public List<RelayRoomState> findRoomsForConnectionReconciliation(int limit) {
        return scanRoomKeys("connection_reconciliation", limit, limit, this::findRoomForConnectionReconciliation);
    }

    @Override
    public List<RelayRoomState> findEmptyWaitingRooms(int limit) {
        return scanRoomKeys("empty_waiting", limit, limit, this::findEmptyWaitingRoom);
    }

    /**
     * Redis room key를 SCAN하며 최종 결과물 생성 대기 상태인 방만 골라냅니다.
     */
    @Override
    public List<RelayRoomState> findFinalizingRooms(int limit) {
        return scanRoomKeys("finalizing", limit, limit, this::findFinalizingRoom);
    }

    /**
     * Redis room key를 SCAN하며 close 기준 시각을 지난 FINISHED 방만 골라냅니다.
     */
    @Override
    public List<RelayRoomState> findClosableFinishedRooms(LocalDateTime closeCutoff, int limit) {
        return scanRoomKeys("closable_finished", limit, limit,
            roomKey -> findClosableFinishedRoom(roomKey, closeCutoff));
    }

    /**
     * 백오피스 관리 화면용 — Redis room key를 SCAN하며 CLOSED를 제외한 모든 활성 방을 모읍니다.
     */
    @Override
    public List<RelayRoomState> findAllActiveRooms() {
        return findActiveRoomsByStatuses(NON_CLOSED_STATUSES);
    }

    /**
     * 백오피스 관리 화면용 — 상태별 ZSET 인덱스로 필요한 활성 방만 조회합니다.
     */
    @Override
    public List<RelayRoomState> findActiveRoomsByStatuses(Set<RelayRoomStatus> statuses) {
        Set<RelayRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty()) {
            return List.of();
        }

        long startedNanos = System.nanoTime();
        if (isActiveRoomIndexUninitialized()) {
            List<RelayRoomState> rooms = scanRoomKeys("active_rooms_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();

            return rooms.stream().filter(room -> statusFilter.contains(room.status())).toList();
        }

        List<RelayRoomState> rooms = findActiveRoomsByIndex(statusFilter, startedNanos);
        log.debug("relay room indexed lookup completed. status_count={} matched_room_count={} duration_ms={}",
            statusFilter.size(), rooms.size(), Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return rooms;
    }

    @Override
    public RelayActiveRoomPage findActiveRoomsByStatuses(Set<RelayRoomStatus> statuses, int page, int size) {
        Set<RelayRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty() || page < 0 || size <= 0) {
            return new RelayActiveRoomPage(List.of(), 0L);
        }

        long startedNanos = System.nanoTime();
        if (isActiveRoomIndexUninitialized()) {
            List<RelayRoomState> rooms = scanRoomKeys("active_rooms_page_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();
            List<RelayRoomState> filtered = rooms.stream().filter(room -> statusFilter.contains(room.status()))
                .sorted(activeRoomComparator()).toList();
            long totalElements = filtered.size();
            int fromIndex = Math.min(page * size, filtered.size());
            int toIndex = Math.min(fromIndex + size, filtered.size());

            return new RelayActiveRoomPage(filtered.subList(fromIndex, toIndex), totalElements);
        }

        List<RelayRoomState> window = findActiveRoomWindowByIndex(statusFilter, page, size, startedNanos);
        long totalElements = countActiveRoomIndexes(statusFilter);
        int fromIndex = Math.min(page * size, window.size());
        int toIndex = Math.min(fromIndex + size, window.size());
        List<RelayRoomState> items = window.stream().sorted(activeRoomComparator()).toList().subList(fromIndex,
            toIndex);
        log.debug(
            "relay room indexed page completed. page={} size={} status_count={} item_count={} total_elements={} "
                + "duration_ms={}",
            page, size, statusFilter.size(), items.size(), totalElements,
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return new RelayActiveRoomPage(items, totalElements);
    }

    @Override
    public long countActiveRoomsByStatuses(Set<RelayRoomStatus> statuses) {
        Set<RelayRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty()) {
            return 0L;
        }

        if (isActiveRoomIndexUninitialized()) {
            List<RelayRoomState> rooms = scanRoomKeys("active_rooms_count_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();

            return rooms.stream().filter(room -> statusFilter.contains(room.status())).count();
        }

        return countActiveRoomIndexes(statusFilter);
    }

    /**
     * Redis room key를 SCAN해서 CLOSED 상태인 방을 cleanup 후보로 모읍니다.
     */
    @Override
    public List<RelayRoomState> findClosedRooms(int limit) {
        return scanRoomKeys("closed_cleanup", limit, limit, this::findClosedRoom);
    }

    /**
     * 여러 서버나 스케줄 tick이 같은 방을 동시에 최종화하지 못하도록 lock을 잡습니다.
     */
    @Override
    public boolean acquireFinalizationLock(String roomCode, String token, Duration ttl) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(createFinalizationLockKey(roomCode), token, ttl);

        return Boolean.TRUE.equals(locked);
    }

    /**
     * 최종화 시도 후 lock을 해제합니다.
     */
    @Override
    public void releaseFinalizationLock(String roomCode, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(createFinalizationLockKey(roomCode)), token);
    }

    /**
     * CLOSED 방 임시 파일 cleanup 완료 marker가 있는지 확인합니다.
     */
    @Override
    public boolean isTempCleanupMarked(String roomCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createTempCleanupMarkerKey(roomCode)));
    }

    /**
     * cleanup 완료 marker는 room state와 분리해서 저장합니다.
     */
    @Override
    public void markTempCleanup(String roomCode, LocalDateTime cleanedAt, Duration ttl) {
        redisTemplate.opsForValue().set(createTempCleanupMarkerKey(roomCode), cleanedAt.toString(), ttl);
    }

    /**
     * 여러 scheduler가 같은 CLOSED 방의 임시 파일을 동시에 삭제하지 않도록 lock을 얻습니다.
     */
    @Override
    public boolean acquireTempCleanupLock(String roomCode, Duration ttl) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(createTempCleanupLockKey(roomCode), "locked", ttl);

        return Boolean.TRUE.equals(locked);
    }

    /**
     * CLOSED 방 임시 파일 cleanup lock을 해제합니다.
     */
    @Override
    public void releaseTempCleanupLock(String roomCode) {
        redisTemplate.delete(createTempCleanupLockKey(roomCode));
    }

    private List<RelayRoomState> scanRoomKeys(String purpose, int scanCount, int matchedLimit,
        Function<String, Optional<RelayRoomState>> matcher) {
        if (scanCount <= 0 || matchedLimit <= 0) {
            return List.of();
        }

        long startedNanos = System.nanoTime();
        int scannedKeyCount = 0;
        List<RelayRoomState> matchedRooms = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(scanCount).build();

        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && matchedRooms.size() < matchedLimit) {
                scannedKeyCount++;
                matcher.apply(roomKeys.next()).ifPresent(matchedRooms::add);
            }
        }

        log.debug(
            "relay room scan completed. purpose={} scanned_key_count={} matched_room_count={} limit={} duration_ms={}",
            purpose, scannedKeyCount, matchedRooms.size(), matchedLimit,
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return matchedRooms;
    }

    private Optional<RelayRoomState> findExpiredPlayingRoom(String roomKey, LocalDateTime now) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null
            || roomState.partDeadlineAt() == null || roomState.partDeadlineAt().isAfter(now)) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private Optional<RelayRoomState> findPlayingRoomForDisconnectGrace(String roomKey, LocalDateTime disconnectCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.PLAYING || roomState.currentPart() == null) {
            return Optional.empty();
        }

        if (hasExpiredDisconnectedParticipant(roomState, disconnectCutoff)
            || hasDroppedParticipantPendingCurrentAssignment(roomState)
            || hasDroppedHostWithConnectedCandidate(roomState)) {
            return Optional.of(roomState);
        }

        return Optional.empty();
    }

    private boolean hasExpiredDisconnectedParticipant(RelayRoomState roomState, LocalDateTime disconnectCutoff) {
        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && !participant.connected()
                && participant.disconnectedAt() != null && !participant.disconnectedAt().isAfter(disconnectCutoff));
    }

    private boolean hasDroppedParticipantPendingCurrentAssignment(RelayRoomState roomState) {
        List<String> droppedUserUuids = roomState.participants().stream().filter(participant -> participant.dropped())
            .map(participant -> participant.userUuid()).toList();

        if (droppedUserUuids.isEmpty()) {
            return false;
        }

        return roomState.assignments().stream()
            .anyMatch(assignment -> assignment.part() == roomState.currentPart()
                && assignment.status() == RelayAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid()));
    }

    private boolean hasDroppedHostWithConnectedCandidate(RelayRoomState roomState) {
        boolean droppedHostExists = roomState.participants().stream().anyMatch(participant -> participant.dropped()
            && (participant.host() || participant.userUuid().equals(roomState.hostUserUuid())));
        if (!droppedHostExists) {
            return false;
        }

        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && participant.connected());
    }

    private Optional<RelayRoomState> findRoomForConnectionReconciliation(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.WAITING && roomState.status() != RelayRoomStatus.PLAYING) {
            return Optional.empty();
        }

        boolean hasConnectedParticipant = roomState.participants().stream().anyMatch(RelayRoomParticipant::connected);
        return hasConnectedParticipant ? Optional.of(roomState) : Optional.empty();
    }

    private Optional<RelayRoomState> findEmptyWaitingRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() == RelayRoomStatus.WAITING && roomState.participants().isEmpty()) {
            return Optional.of(roomState);
        }

        return Optional.empty();
    }

    private Optional<RelayRoomState> findAbandonedWaitingRoom(String roomKey, LocalDateTime idleCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.WAITING || roomState.participants().isEmpty()) {
            return Optional.empty();
        }

        if (!allParticipantsDisconnected(roomState)) {
            return Optional.empty();
        }

        LocalDateTime idleSince = latestWaitingInactiveAt(roomState);
        if (idleSince == null || idleSince.isAfter(idleCutoff)) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private Optional<RelayRoomState> findAbandonedPlayingRoom(String roomKey, LocalDateTime abandonedCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.PLAYING || roomState.participants().isEmpty()) {
            return Optional.empty();
        }

        if (!allParticipantsInactiveInPlaying(roomState)) {
            return Optional.empty();
        }

        LocalDateTime inactiveSince = latestPlayingInactiveAt(roomState);
        if (inactiveSince == null || inactiveSince.isAfter(abandonedCutoff)) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private boolean allParticipantsDisconnected(RelayRoomState roomState) {
        return roomState.participants().stream().allMatch(participant -> !participant.connected());
    }

    private boolean allParticipantsInactiveInPlaying(RelayRoomState roomState) {
        return roomState.participants().stream()
            .allMatch(participant -> participant.dropped() || !participant.connected());
    }

    private LocalDateTime latestWaitingInactiveAt(RelayRoomState roomState) {
        LocalDateTime latest = roomState.updatedAt();
        for (RelayRoomParticipant participant : roomState.participants()) {
            latest = maxTime(latest, participant.disconnectedAt());
        }

        return latest;
    }

    private LocalDateTime latestPlayingInactiveAt(RelayRoomState roomState) {
        LocalDateTime latest = roomState.updatedAt();
        for (RelayRoomParticipant participant : roomState.participants()) {
            latest = maxTime(latest, participant.disconnectedAt());
            latest = maxTime(latest, participant.droppedAt());
        }

        return latest;
    }

    private LocalDateTime maxTime(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        return left.isAfter(right) ? left : right;
    }

    /**
     * SCAN으로 발견한 Redis 값이 실제 FINALIZING 방인지 확인합니다.
     */
    private Optional<RelayRoomState> findFinalizingRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.FINALIZING) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    /**
     * SCAN으로 발견한 Redis 값이 CLOSED를 제외한 활성 방인지 확인합니다.
     */
    private Optional<RelayRoomState> findActiveRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() == RelayRoomStatus.CLOSED) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    /**
     * SCAN으로 찾은 Redis 값이 실제 CLOSED 방인지 확인합니다.
     */
    private Optional<RelayRoomState> findClosedRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.CLOSED) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private Optional<RelayRoomState> findClosableFinishedRoom(String roomKey, LocalDateTime closeCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        RelayRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != RelayRoomStatus.FINISHED || roomState.updatedAt() == null
            || roomState.updatedAt().isAfter(closeCutoff)) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private List<RelayRoomState> findActiveRoomsByIndex(Set<RelayRoomStatus> statusFilter, long startedNanos) {
        return findActiveRoomsByIndex(statusFilter, 0, -1, startedNanos);
    }

    private List<RelayRoomState> findActiveRoomWindowByIndex(Set<RelayRoomStatus> statusFilter, int page, int size,
        long startedNanos) {
        long endOffset = (long) page * size + size - 1;

        return findActiveRoomsByIndex(statusFilter, 0, endOffset, startedNanos);
    }

    private List<RelayRoomState> findActiveRoomsByIndex(Set<RelayRoomStatus> statusFilter, long startOffset,
        long endOffset, long startedNanos) {
        Set<String> seenRoomCodes = new LinkedHashSet<>();
        List<RelayRoomState> rooms = new ArrayList<>();
        int fetchedRoomCount = 0;

        for (RelayRoomStatus indexedStatus : statusFilter) {
            String indexKey = createActiveRoomIndexKey(indexedStatus);
            Set<String> roomCodes = redisTemplate.opsForZSet().reverseRange(indexKey, startOffset, endOffset);
            if (roomCodes == null || roomCodes.isEmpty()) {
                continue;
            }

            fetchedRoomCount += roomCodes.size();
            for (String roomCode : roomCodes) {
                Optional<RelayRoomState> roomState = findByRoomCode(roomCode);
                if (roomState.isEmpty()) {
                    removeActiveRoomIndexes(roomCode);
                    continue;
                }

                RelayRoomState restoredRoomState = roomState.get();
                if (restoredRoomState.status() == RelayRoomStatus.CLOSED) {
                    removeActiveRoomIndexes(roomCode);
                    continue;
                }

                if (restoredRoomState.status() != indexedStatus) {
                    syncActiveRoomIndex(restoredRoomState);
                }

                if (statusFilter.contains(restoredRoomState.status())
                    && seenRoomCodes.add(restoredRoomState.roomCode())) {
                    rooms.add(restoredRoomState);
                }
            }
        }

        log.debug(
            "relay room index read completed. status_count={} fetched_room_count={} matched_room_count={} "
                + "duration_ms={}",
            statusFilter.size(), fetchedRoomCount, rooms.size(),
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        return rooms;
    }

    private long countActiveRoomIndexes(Set<RelayRoomStatus> statusFilter) {
        removeExpiredActiveRoomIndexes(statusFilter);

        long total = 0L;
        for (RelayRoomStatus status : statusFilter) {
            Long indexedRoomCount = redisTemplate.opsForZSet().zCard(createActiveRoomIndexKey(status));
            total += indexedRoomCount == null ? 0L : indexedRoomCount;
        }

        return total;
    }

    private Set<RelayRoomStatus> normalizeActiveStatuses(Set<RelayRoomStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }

        EnumSet<RelayRoomStatus> normalized = EnumSet.noneOf(RelayRoomStatus.class);
        for (RelayRoomStatus status : statuses) {
            if (status != null && status != RelayRoomStatus.CLOSED) {
                normalized.add(status);
            }
        }

        return normalized;
    }

    private boolean isActiveRoomIndexUninitialized() {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_ROOM_INDEX_INITIALIZED_KEY));
    }

    private void markActiveRoomIndexInitialized() {
        redisTemplate.opsForValue().set(ACTIVE_ROOM_INDEX_INITIALIZED_KEY, "true", RelayRoomRepository.ROOM_STATE_TTL);
    }

    private void syncActiveRoomIndex(RelayRoomState roomState) {
        syncActiveRoomIndex(redisTemplate, roomState);
    }

    private void syncActiveRoomIndex(RedisOperations<String, String> operations, RelayRoomState roomState) {
        removeActiveRoomIndexes(operations, roomState.roomCode());
        if (roomState.status() == RelayRoomStatus.CLOSED) {
            return;
        }

        var zSetOperations = operations.opsForZSet();
        if (zSetOperations == null) {
            return;
        }

        String indexKey = createActiveRoomIndexKey(roomState.status());
        zSetOperations.add(indexKey, roomState.roomCode(), toCreatedAtScore(roomState.createdAt()));
        operations.expire(indexKey, RelayRoomRepository.ROOM_STATE_TTL);

        String expiryIndexKey = createActiveRoomExpiryIndexKey(roomState.status());
        zSetOperations.add(expiryIndexKey, roomState.roomCode(), toExpiresAtScore());
        operations.expire(expiryIndexKey, RelayRoomRepository.ROOM_STATE_TTL);
    }

    private void removeActiveRoomIndexes(String roomCode) {
        removeActiveRoomIndexes(redisTemplate, roomCode);
    }

    private void removeActiveRoomIndexes(RedisOperations<String, String> operations, String roomCode) {
        if (!StringUtils.hasText(roomCode)) {
            return;
        }

        var zSetOperations = operations.opsForZSet();
        if (zSetOperations == null) {
            return;
        }

        for (RelayRoomStatus status : NON_CLOSED_STATUSES) {
            zSetOperations.remove(createActiveRoomIndexKey(status), roomCode);
            zSetOperations.remove(createActiveRoomExpiryIndexKey(status), roomCode);
        }
    }

    private void removeExpiredActiveRoomIndexes(Set<RelayRoomStatus> statusFilter) {
        var zSetOperations = redisTemplate.opsForZSet();
        if (zSetOperations == null) {
            return;
        }

        double nowScore = System.currentTimeMillis();
        for (RelayRoomStatus status : statusFilter) {
            Set<String> expiredRoomCodes = zSetOperations.rangeByScore(createActiveRoomExpiryIndexKey(status), 0,
                nowScore);
            if (expiredRoomCodes == null || expiredRoomCodes.isEmpty()) {
                continue;
            }

            for (String roomCode : expiredRoomCodes) {
                removeActiveRoomIndexes(roomCode);
            }
        }
    }

    private String createActiveRoomIndexKey(RelayRoomStatus status) {
        return ACTIVE_ROOM_INDEX_KEY_PREFIX + status.name();
    }

    private String createActiveRoomExpiryIndexKey(RelayRoomStatus status) {
        return ACTIVE_ROOM_EXPIRY_INDEX_KEY_PREFIX + status.name();
    }

    private double toCreatedAtScore(LocalDateTime createdAt) {
        LocalDateTime safeCreatedAt = createdAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : createdAt;

        return safeCreatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private double toExpiresAtScore() {
        return System.currentTimeMillis() + RelayRoomRepository.ROOM_STATE_TTL.toMillis();
    }

    private Comparator<RelayRoomState> activeRoomComparator() {
        return Comparator.comparing(RelayRoomState::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(RelayRoomState::roomCode, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private String createRoomKey(String roomCode) {
        // roomCode는 공유 링크, 실시간 연결 식별자, Redis key에서 같은 값을 그대로 사용합니다.
        return ROOM_KEY_PREFIX + roomCode;
    }

    /**
     * lock key가 room scan에 걸리지 않도록 relay:room: prefix와 분리합니다.
     */
    private String createFinalizationLockKey(String roomCode) {
        return FINALIZATION_LOCK_KEY_PREFIX + roomCode;
    }

    private String createTempCleanupMarkerKey(String roomCode) {
        return TEMP_CLEANUP_MARKER_KEY_PREFIX + roomCode;
    }

    private String createTempCleanupLockKey(String roomCode) {
        return TEMP_CLEANUP_LOCK_KEY_PREFIX + roomCode;
    }

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> castToStringOperations(RedisOperations<?, ?> operations) {
        return (RedisOperations<String, String>) operations;
    }

    /**
     * 문자열 직접 조립 대신 Jackson으로 Redis 저장 JSON을 생성합니다.
     */
    private String serialize(RelayRoomState roomState) {
        try {
            return objectMapper.writeValueAsString(roomState);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ROOM_STATE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    /**
     * Redis 문자열 JSON을 도메인 모델로 복원해 서비스가 방 상태 정책을 계산할 수 있게 합니다.
     */
    private RelayRoomState deserialize(String roomStateValue) {
        try {
            return objectMapper.readValue(roomStateValue, RelayRoomState.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
