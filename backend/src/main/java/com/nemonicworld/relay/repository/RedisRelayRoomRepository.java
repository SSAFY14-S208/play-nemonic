package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 릴레이 방 상태를 Redis 문자열 JSON 값으로 저장하고 조회하는 저장소입니다.
 */
@Repository
public class RedisRelayRoomRepository implements RelayRoomRepository {

    private static final String ROOM_KEY_PREFIX = "relay:room:";
    private static final String FINALIZATION_LOCK_KEY_PREFIX = "relay:room-finalization-lock:";
    private static final String TEMP_CLEANUP_MARKER_KEY_PREFIX = "relay:room-temp-cleanup:";
    private static final String TEMP_CLEANUP_LOCK_KEY_PREFIX = "relay:room-temp-cleanup-lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "릴레이 방 상태를 저장할 수 없습니다.";
    private static final String ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE = "릴레이 방 상태를 읽을 수 없습니다.";

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
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<RelayRoomState> expiredRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && expiredRooms.size() < limit) {
                findExpiredPlayingRoom(roomKeys.next(), now).ifPresent(expiredRooms::add);
            }
        }

        return expiredRooms;
    }

    /**
     * Redis room key를 SCAN하며 이탈 확정 또는 이탈자 현재 배정 자동 제출이 필요한 PLAYING 방만 조회합니다.
     */
    @Override
    public List<RelayRoomState> findPlayingRoomsForDisconnectGrace(LocalDateTime disconnectCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<RelayRoomState> candidateRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && candidateRooms.size() < limit) {
                findPlayingRoomForDisconnectGrace(roomKeys.next(), disconnectCutoff).ifPresent(candidateRooms::add);
            }
        }

        return candidateRooms;
    }

    /**
     * Redis room key를 SCAN하며 최종 결과물 생성 대기 상태인 방만 골라냅니다.
     */
    @Override
    public List<RelayRoomState> findFinalizingRooms(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<RelayRoomState> finalizingRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && finalizingRooms.size() < limit) {
                findFinalizingRoom(roomKeys.next()).ifPresent(finalizingRooms::add);
            }
        }

        return finalizingRooms;
    }

    /**
     * Redis room key를 SCAN하며 close 기준 시각을 지난 FINISHED 방만 골라냅니다.
     */
    @Override
    public List<RelayRoomState> findClosableFinishedRooms(LocalDateTime closeCutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<RelayRoomState> closableRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && closableRooms.size() < limit) {
                findClosableFinishedRoom(roomKeys.next(), closeCutoff).ifPresent(closableRooms::add);
            }
        }

        return closableRooms;
    }

    /**
     * 백오피스 관리 화면용 — Redis room key를 SCAN하며 CLOSED를 제외한 모든 활성 방을 모읍니다.
     */
    @Override
    public List<RelayRoomState> findAllActiveRooms() {
        // SCAN count는 Redis 내부 페이지 힌트일 뿐 결과 상한이 아닙니다.
        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(200).build();
        List<RelayRoomState> activeRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext()) {
                findActiveRoom(roomKeys.next()).ifPresent(activeRooms::add);
            }
        }

        return activeRooms;
    }

    /**
     * Redis room key를 SCAN해서 CLOSED 상태인 방을 cleanup 후보로 모읍니다.
     */
    @Override
    public List<RelayRoomState> findClosedRooms(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(limit).build();
        List<RelayRoomState> closedRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext() && closedRooms.size() < limit) {
                findClosedRoom(roomKeys.next()).ifPresent(closedRooms::add);
            }
        }

        return closedRooms;
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
