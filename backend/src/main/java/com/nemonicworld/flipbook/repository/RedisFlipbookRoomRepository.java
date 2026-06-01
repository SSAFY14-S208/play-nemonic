package com.nemonicworld.flipbook.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 상태를 Redis 문자열 JSON 값으로 저장하고 조회하는 저장소입니다.
 */
@Repository
public class RedisFlipbookRoomRepository implements FlipbookRoomRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisFlipbookRoomRepository.class);

    private static final String ROOM_KEY_PREFIX = "flipbook:room:";
    private static final String ACTIVE_ROOM_INDEX_KEY_PREFIX = "flipbook:rooms:active:created-at:";
    private static final String ACTIVE_ROOM_EXPIRY_INDEX_KEY_PREFIX = "flipbook:rooms:active:expires-at:";
    private static final String ACTIVE_ROOM_INDEX_INITIALIZED_KEY = "flipbook:rooms:active:index-initialized";
    private static final String FINALIZATION_LOCK_KEY_PREFIX = "flipbook:room-finalization-lock:";
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 저장할 수 없습니다.";
    private static final String ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 읽을 수 없습니다.";
    private static final Set<FlipbookRoomStatus> NON_CLOSED_STATUSES = EnumSet.of(FlipbookRoomStatus.WAITING,
        FlipbookRoomStatus.PLAYING, FlipbookRoomStatus.FINALIZING, FlipbookRoomStatus.FINISHED);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
          return redis.call('DEL', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFlipbookRoomRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
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
     * 진행 중 방 상태를 24시간 TTL로 저장합니다.
     */
    @Override
    public void save(FlipbookRoomState roomState) {
        redisTemplate.opsForValue().set(createRoomKey(roomState.roomCode()), serialize(roomState),
            FlipbookRoomRepository.ROOM_STATE_TTL);
        syncActiveRoomIndex(roomState);
    }

    /**
     * WATCH/MULTI/EXEC를 사용해 기대한 Redis 상태가 유지될 때만 새 상태를 저장합니다.
     */
    @Override
    public boolean saveIfUnchanged(FlipbookRoomState expectedRoomState, FlipbookRoomState updatedRoomState) {
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

                FlipbookRoomState currentRoomState = deserialize(currentRoomStateValue);

                if (!currentRoomState.equals(expectedRoomState)) {
                    stringOperations.unwatch();
                    return false;
                }

                stringOperations.multi();
                stringOperations.opsForValue().set(roomKey, serialize(updatedRoomState),
                    FlipbookRoomRepository.ROOM_STATE_TTL);
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
    public Optional<FlipbookRoomState> findByRoomCode(String roomCode) {
        String roomStateValue = redisTemplate.opsForValue().get(createRoomKey(roomCode));

        if (!StringUtils.hasText(roomStateValue)) { // 해당하는 값이 없으면
            return Optional.empty(); // 값이 없음
        }

        return Optional.of(deserialize(roomStateValue)); // 해당하는 값이 있으면 역직렬화해서 객체화한 후 객체를 반환 (메타데이타 정보)
    }

    /**
     * 백오피스 관리 화면용 — Redis room key를 SCAN하며 CLOSED를 제외한 모든 활성 방을 모읍니다.
     */
    @Override
    public List<FlipbookRoomState> findAllActiveRooms() {
        return findActiveRoomsByStatuses(NON_CLOSED_STATUSES);
    }

    /**
     * 백오피스 관리 화면용 — 상태별 ZSET 인덱스로 필요한 활성 방만 조회합니다.
     */
    @Override
    public List<FlipbookRoomState> findActiveRoomsByStatuses(Set<FlipbookRoomStatus> statuses) {
        Set<FlipbookRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty()) {
            return List.of();
        }

        long startedNanos = System.nanoTime();
        if (isActiveRoomIndexUninitialized()) {
            List<FlipbookRoomState> rooms = scanRoomKeys("active_rooms_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();

            return rooms.stream().filter(room -> statusFilter.contains(room.status())).toList();
        }

        List<FlipbookRoomState> rooms = findActiveRoomsByIndex(statusFilter, startedNanos);
        log.debug("flipbook room indexed lookup completed. status_count={} matched_room_count={} duration_ms={}",
            statusFilter.size(), rooms.size(), Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return rooms;
    }

    @Override
    public FlipbookActiveRoomPage findActiveRoomsByStatuses(Set<FlipbookRoomStatus> statuses, int page, int size) {
        Set<FlipbookRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty() || page < 0 || size <= 0) {
            return new FlipbookActiveRoomPage(List.of(), 0L);
        }

        long startedNanos = System.nanoTime();
        if (isActiveRoomIndexUninitialized()) {
            List<FlipbookRoomState> rooms = scanRoomKeys("active_rooms_page_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();
            List<FlipbookRoomState> filtered = rooms.stream().filter(room -> statusFilter.contains(room.status()))
                .sorted(activeRoomComparator()).toList();
            long totalElements = filtered.size();
            int fromIndex = Math.min(page * size, filtered.size());
            int toIndex = Math.min(fromIndex + size, filtered.size());

            return new FlipbookActiveRoomPage(filtered.subList(fromIndex, toIndex), totalElements);
        }

        List<FlipbookRoomState> window = findActiveRoomWindowByIndex(statusFilter, page, size, startedNanos);
        long totalElements = countActiveRoomIndexes(statusFilter);
        int fromIndex = Math.min(page * size, window.size());
        int toIndex = Math.min(fromIndex + size, window.size());
        List<FlipbookRoomState> items = window.stream().sorted(activeRoomComparator()).toList().subList(fromIndex,
            toIndex);
        log.debug(
            "flipbook room indexed page completed. page={} size={} status_count={} item_count={} total_elements={} "
                + "duration_ms={}",
            page, size, statusFilter.size(), items.size(), totalElements,
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return new FlipbookActiveRoomPage(items, totalElements);
    }

    @Override
    public long countActiveRoomsByStatuses(Set<FlipbookRoomStatus> statuses) {
        Set<FlipbookRoomStatus> statusFilter = normalizeActiveStatuses(statuses);
        if (statusFilter.isEmpty()) {
            return 0L;
        }

        if (isActiveRoomIndexUninitialized()) {
            List<FlipbookRoomState> rooms = scanRoomKeys("active_rooms_count_fallback", 200, Integer.MAX_VALUE,
                this::findActiveRoom);
            rooms.forEach(this::syncActiveRoomIndex);
            markActiveRoomIndexInitialized();

            return rooms.stream().filter(room -> statusFilter.contains(room.status())).count();
        }

        return countActiveRoomIndexes(statusFilter);
    }

    /**
     * Redis room key를 SCAN하며 이탈 확정 처리가 필요한 PLAYING 방만 조회합니다.
     */
    @Override
    public List<FlipbookRoomState> findPlayingRoomsForDisconnectGrace(LocalDateTime disconnectCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> candidateRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && candidateRooms.size() < limit) {
                findPlayingRoomForDisconnectGrace(roomKeys.next(), disconnectCutoff).ifPresent(candidateRooms::add);
            }
        }

        return candidateRooms;
    }

    @Override
    public List<FlipbookRoomState> findAbandonedWaitingRooms(LocalDateTime idleCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> abandonedRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && abandonedRooms.size() < limit) {
                findAbandonedWaitingRoom(roomKeys.next(), idleCutoff).ifPresent(abandonedRooms::add);
            }
        }

        return abandonedRooms;
    }

    @Override
    public List<FlipbookRoomState> findAbandonedPlayingRooms(LocalDateTime abandonedCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> abandonedRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && abandonedRooms.size() < limit) {
                findAbandonedPlayingRoom(roomKeys.next(), abandonedCutoff).ifPresent(abandonedRooms::add);
            }
        }

        return abandonedRooms;
    }

    @Override
    public List<FlipbookRoomState> findEmptyWaitingRooms(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> emptyRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && emptyRooms.size() < limit) {
                findEmptyWaitingRoom(roomKeys.next()).ifPresent(emptyRooms::add);
            }
        }

        return emptyRooms;
    }

    /**
     * Redis room key를 SCAN하며 현재 라운드 마감 시각이 지난 PLAYING 방만 조회합니다.
     */
    @Override
    public List<FlipbookRoomState> findExpiredPlayingRooms(LocalDateTime roundDeadlineCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> expiredRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && expiredRooms.size() < limit) {
                findExpiredPlayingRoom(roomKeys.next(), roundDeadlineCutoff).ifPresent(expiredRooms::add);
            }
        }

        return expiredRooms;
    }

    /**
     * Redis room key를 SCAN하며 FINALIZING 방만 조회합니다.
     */
    @Override
    public List<FlipbookRoomState> findFinalizingRooms(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> finalizingRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && finalizingRooms.size() < limit) {
                findFinalizingRoom(roomKeys.next()).ifPresent(finalizingRooms::add);
            }
        }

        return finalizingRooms;
    }

    /**
     * Redis room key를 SCAN하며 close 기준 시각을 지난 FINISHED 방만 조회합니다.
     */
    @Override
    public List<FlipbookRoomState> findClosableFinishedRooms(LocalDateTime closeCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<FlipbookRoomState> closableRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && closableRooms.size() < limit) {
                findClosableFinishedRoom(roomKeys.next(), closeCutoff).ifPresent(closableRooms::add);
            }
        }

        return closableRooms;
    }

    /**
     * 같은 방 결과 생성을 여러 서버가 동시에 처리하지 않도록 짧은 TTL lock을 획득합니다.
     */
    @Override
    public boolean acquireFinalizationLock(String roomCode, String token, Duration ttl) {
        return Boolean.TRUE
            .equals(redisTemplate.opsForValue().setIfAbsent(createFinalizationLockKey(roomCode), token, ttl));
    }

    /**
     * lock을 획득한 처리자만 해제할 수 있도록 Lua로 token을 비교한 뒤 삭제합니다.
     */
    @Override
    public void releaseFinalizationLock(String roomCode, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(createFinalizationLockKey(roomCode)), token);
    }

    private List<FlipbookRoomState> scanRoomKeys(String purpose, int scanCount, int matchedLimit,
        java.util.function.Function<String, Optional<FlipbookRoomState>> matcher) {
        if (scanCount <= 0 || matchedLimit <= 0) {
            return List.of();
        }

        long startedNanos = System.nanoTime();
        int scannedKeyCount = 0;
        List<FlipbookRoomState> matchedRooms = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(scanCount).build();

        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && matchedRooms.size() < matchedLimit) {
                scannedKeyCount++;
                matcher.apply(roomKeys.next()).ifPresent(matchedRooms::add);
            }
        }

        log.debug(
            "flipbook room scan completed. purpose={} scanned_key_count={} matched_room_count={} limit={} "
                + "duration_ms={}",
            purpose, scannedKeyCount, matchedRooms.size(), matchedLimit,
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());

        return matchedRooms;
    }

    private List<FlipbookRoomState> findActiveRoomsByIndex(Set<FlipbookRoomStatus> statusFilter, long startedNanos) {
        return findActiveRoomsByIndex(statusFilter, 0, -1, startedNanos);
    }

    private List<FlipbookRoomState> findActiveRoomWindowByIndex(Set<FlipbookRoomStatus> statusFilter, int page,
        int size, long startedNanos) {
        long endOffset = (long) page * size + size - 1;

        return findActiveRoomsByIndex(statusFilter, 0, endOffset, startedNanos);
    }

    private List<FlipbookRoomState> findActiveRoomsByIndex(Set<FlipbookRoomStatus> statusFilter, long startOffset,
        long endOffset, long startedNanos) {
        Set<String> seenRoomCodes = new LinkedHashSet<>();
        List<FlipbookRoomState> rooms = new ArrayList<>();
        int fetchedRoomCount = 0;

        for (FlipbookRoomStatus indexedStatus : statusFilter) {
            String indexKey = createActiveRoomIndexKey(indexedStatus);
            Set<String> roomCodes = redisTemplate.opsForZSet().reverseRange(indexKey, startOffset, endOffset);
            if (roomCodes == null || roomCodes.isEmpty()) {
                continue;
            }

            fetchedRoomCount += roomCodes.size();
            for (String roomCode : roomCodes) {
                Optional<FlipbookRoomState> roomState = findByRoomCode(roomCode);
                if (roomState.isEmpty()) {
                    removeActiveRoomIndexes(roomCode);
                    continue;
                }

                FlipbookRoomState restoredRoomState = roomState.get();
                if (restoredRoomState.status() == FlipbookRoomStatus.CLOSED) {
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
            "flipbook room index read completed. status_count={} fetched_room_count={} matched_room_count={} "
                + "duration_ms={}",
            statusFilter.size(), fetchedRoomCount, rooms.size(),
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        return rooms;
    }

    private long countActiveRoomIndexes(Set<FlipbookRoomStatus> statusFilter) {
        removeExpiredActiveRoomIndexes(statusFilter);

        long total = 0L;
        for (FlipbookRoomStatus status : statusFilter) {
            Long indexedRoomCount = redisTemplate.opsForZSet().zCard(createActiveRoomIndexKey(status));
            total += indexedRoomCount == null ? 0L : indexedRoomCount;
        }

        return total;
    }

    private Set<FlipbookRoomStatus> normalizeActiveStatuses(Set<FlipbookRoomStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }

        EnumSet<FlipbookRoomStatus> normalized = EnumSet.noneOf(FlipbookRoomStatus.class);
        for (FlipbookRoomStatus status : statuses) {
            if (status != null && status != FlipbookRoomStatus.CLOSED) {
                normalized.add(status);
            }
        }

        return normalized;
    }

    private boolean isActiveRoomIndexUninitialized() {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_ROOM_INDEX_INITIALIZED_KEY));
    }

    private void markActiveRoomIndexInitialized() {
        redisTemplate.opsForValue().set(ACTIVE_ROOM_INDEX_INITIALIZED_KEY, "true",
            FlipbookRoomRepository.ROOM_STATE_TTL);
    }

    private void syncActiveRoomIndex(FlipbookRoomState roomState) {
        syncActiveRoomIndex(redisTemplate, roomState);
    }

    private void syncActiveRoomIndex(RedisOperations<String, String> operations, FlipbookRoomState roomState) {
        removeActiveRoomIndexes(operations, roomState.roomCode());
        if (roomState.status() == FlipbookRoomStatus.CLOSED) {
            return;
        }

        var zSetOperations = operations.opsForZSet();
        if (zSetOperations == null) {
            return;
        }

        String indexKey = createActiveRoomIndexKey(roomState.status());
        zSetOperations.add(indexKey, roomState.roomCode(), toCreatedAtScore(roomState.createdAt()));
        operations.expire(indexKey, FlipbookRoomRepository.ROOM_STATE_TTL);

        String expiryIndexKey = createActiveRoomExpiryIndexKey(roomState.status());
        zSetOperations.add(expiryIndexKey, roomState.roomCode(), toExpiresAtScore());
        operations.expire(expiryIndexKey, FlipbookRoomRepository.ROOM_STATE_TTL);
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

        for (FlipbookRoomStatus status : NON_CLOSED_STATUSES) {
            zSetOperations.remove(createActiveRoomIndexKey(status), roomCode);
            zSetOperations.remove(createActiveRoomExpiryIndexKey(status), roomCode);
        }
    }

    private void removeExpiredActiveRoomIndexes(Set<FlipbookRoomStatus> statusFilter) {
        var zSetOperations = redisTemplate.opsForZSet();
        if (zSetOperations == null) {
            return;
        }

        double nowScore = System.currentTimeMillis();
        for (FlipbookRoomStatus status : statusFilter) {
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

    private String createActiveRoomIndexKey(FlipbookRoomStatus status) {
        return ACTIVE_ROOM_INDEX_KEY_PREFIX + status.name();
    }

    private String createActiveRoomExpiryIndexKey(FlipbookRoomStatus status) {
        return ACTIVE_ROOM_EXPIRY_INDEX_KEY_PREFIX + status.name();
    }

    private double toCreatedAtScore(LocalDateTime createdAt) {
        LocalDateTime safeCreatedAt = createdAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : createdAt;

        return safeCreatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private double toExpiresAtScore() {
        return System.currentTimeMillis() + FlipbookRoomRepository.ROOM_STATE_TTL.toMillis();
    }

    private Comparator<FlipbookRoomState> activeRoomComparator() {
        return Comparator.comparing(FlipbookRoomState::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(FlipbookRoomState::roomCode, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Optional<FlipbookRoomState> findPlayingRoomForDisconnectGrace(String roomKey,
        LocalDateTime disconnectCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.PLAYING || roomState.currentRound() == null) {
            return Optional.empty();
        }

        if (hasExpiredDisconnectedParticipant(roomState, disconnectCutoff)
            || hasDroppedParticipantPendingCurrentAssignment(roomState)
            || hasDroppedHostWithConnectedCandidate(roomState)) {
            return Optional.of(roomState);
        }

        return Optional.empty();
    }

    /**
     * SCAN으로 발견한 Redis 값이 실제 CLOSED를 제외한 활성 방인지 확인합니다.
     */
    private Optional<FlipbookRoomState> findActiveRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() == FlipbookRoomStatus.CLOSED) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private Optional<FlipbookRoomState> findEmptyWaitingRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() == FlipbookRoomStatus.WAITING && roomState.participants().isEmpty()) {
            return Optional.of(roomState);
        }

        return Optional.empty();
    }

    private Optional<FlipbookRoomState> findAbandonedWaitingRoom(String roomKey, LocalDateTime idleCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.WAITING || roomState.participants().isEmpty()) {
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

    private Optional<FlipbookRoomState> findAbandonedPlayingRoom(String roomKey, LocalDateTime abandonedCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.PLAYING || roomState.participants().isEmpty()) {
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

    private Optional<FlipbookRoomState> findExpiredPlayingRoom(String roomKey, LocalDateTime roundDeadlineCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.PLAYING || roomState.currentRound() == null
            || roomState.roundDeadlineAt() == null) {
            return Optional.empty();
        }

        if (!roomState.roundDeadlineAt().isAfter(roundDeadlineCutoff)) {
            return Optional.of(roomState);
        }

        return Optional.empty();
    }

    private Optional<FlipbookRoomState> findFinalizingRoom(String roomKey) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.FINALIZING) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private Optional<FlipbookRoomState> findClosableFinishedRoom(String roomKey, LocalDateTime closeCutoff) {
        String roomStateValue = redisTemplate.opsForValue().get(roomKey);
        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        FlipbookRoomState roomState = deserialize(roomStateValue);
        if (roomState.status() != FlipbookRoomStatus.FINISHED || roomState.updatedAt() == null
            || roomState.updatedAt().isAfter(closeCutoff)) {
            return Optional.empty();
        }

        return Optional.of(roomState);
    }

    private boolean hasExpiredDisconnectedParticipant(FlipbookRoomState roomState, LocalDateTime disconnectCutoff) {
        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && !participant.connected()
                && participant.disconnectedAt() != null && !participant.disconnectedAt().isAfter(disconnectCutoff));
    }

    private boolean hasDroppedParticipantPendingCurrentAssignment(FlipbookRoomState roomState) {
        List<String> droppedUserUuids = roomState.participants().stream().filter(participant -> participant.dropped())
            .map(participant -> participant.userUuid()).toList();

        if (droppedUserUuids.isEmpty()) {
            return false;
        }

        return roomState.assignments().stream()
            .anyMatch(assignment -> assignment.round() == roomState.currentRound()
                && assignment.status() == FlipbookFrameAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid()));
    }

    private boolean hasDroppedHostWithConnectedCandidate(FlipbookRoomState roomState) {
        boolean droppedHostExists = roomState.participants().stream().anyMatch(participant -> participant.dropped()
            && (participant.host() || participant.userUuid().equals(roomState.hostUserUuid())));
        if (!droppedHostExists) {
            return false;
        }

        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && participant.connected());
    }

    private boolean allParticipantsDisconnected(FlipbookRoomState roomState) {
        return roomState.participants().stream().allMatch(participant -> !participant.connected());
    }

    private boolean allParticipantsInactiveInPlaying(FlipbookRoomState roomState) {
        return roomState.participants().stream()
            .allMatch(participant -> participant.dropped() || !participant.connected());
    }

    private LocalDateTime latestWaitingInactiveAt(FlipbookRoomState roomState) {
        LocalDateTime latest = roomState.updatedAt();
        for (FlipbookRoomParticipant participant : roomState.participants()) {
            latest = maxTime(latest, participant.disconnectedAt());
        }

        return latest;
    }

    private LocalDateTime latestPlayingInactiveAt(FlipbookRoomState roomState) {
        LocalDateTime latest = roomState.updatedAt();
        for (FlipbookRoomParticipant participant : roomState.participants()) {
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

    private String createRoomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
    }

    private String createFinalizationLockKey(String roomCode) {
        return FINALIZATION_LOCK_KEY_PREFIX + roomCode;
    }

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> castToStringOperations(RedisOperations<?, ?> operations) {
        return (RedisOperations<String, String>) operations;
    }

    /**
     * 문자열 직접 조립 대신 Jackson으로 Redis 저장 JSON을 생성합니다.
     */
    private String serialize(FlipbookRoomState roomState) {
        try {
            return objectMapper.writeValueAsString(roomState);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ROOM_STATE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    /**
     * Redis 문자열 JSON을 플립북 방 상태 모델로 복원합니다.
     */
    private FlipbookRoomState deserialize(String roomStateValue) {
        try {
            return objectMapper.readValue(roomStateValue, FlipbookRoomState.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
