package com.nemonicworld.flipbook.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 상태를 Redis 문자열 JSON 값으로 저장하고 조회하는 저장소입니다.
 */
@Repository
public class RedisFlipbookRoomRepository implements FlipbookRoomRepository {

    private static final String ROOM_KEY_PREFIX = "flipbook:room:";
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 저장할 수 없습니다.";
    private static final String ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE = "플립북 방 상태를 읽을 수 없습니다.";

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

        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        return Optional.of(deserialize(roomStateValue));
    }

    private String createRoomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
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
            throw new IllegalStateException(ROOM_STATE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    /**
     * Redis 문자열 JSON을 플립북 방 상태 모델로 복원합니다.
     */
    private FlipbookRoomState deserialize(String roomStateValue) {
        try {
            return objectMapper.readValue(roomStateValue, FlipbookRoomState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
