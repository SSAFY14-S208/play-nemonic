package com.nemonicworld.flipbook.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final String ROOM_KEY_PREFIX = "flipbook:room:";
    private static final String FINALIZATION_LOCK_KEY_PREFIX = "flipbook:room-finalization-lock:";
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 저장할 수 없습니다.";
    private static final String ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 읽을 수 없습니다.";
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
        // SCAN count는 Redis 내부 페이지 힌트일 뿐 결과 상한이 아닙니다.
        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(200).build();
        List<FlipbookRoomState> activeRooms = new ArrayList<>();
        try (Cursor<String> roomKeys = redisTemplate.scan(scanOptions)) {
            while (roomKeys.hasNext()) {
                findActiveRoom(roomKeys.next()).ifPresent(activeRooms::add);
            }
        }

        return activeRooms;
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
