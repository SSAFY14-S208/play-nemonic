package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.entity.RelayRoomState;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
/**
 * 릴레이 방 상태를 Redis 문자열 JSON 값으로 저장하고 조회하는 저장소입니다.
 */
public class RedisRelayRoomRepository implements RelayRoomRepository {

    private static final String ROOM_KEY_PREFIX = "relay:room:";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);
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
        redisTemplate.opsForValue().set(createRoomKey(roomState.roomCode()), serialize(roomState), ROOM_STATE_TTL);
    }

    /**
     * Redis에 저장된 JSON을 방 상태 모델로 역직렬화합니다.
     */
    @Override
    public Optional<RelayRoomState> findByRoomCode(String roomCode) {
        String roomStateValue = redisTemplate.opsForValue().get(createRoomKey(roomCode));

        if (!StringUtils.hasText(roomStateValue)) {
            return Optional.empty();
        }

        return Optional.of(deserialize(roomStateValue));
    }

    private String createRoomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
    }

    /**
     * 문자열 직접 조립 대신 Jackson으로 Redis 저장 JSON을 생성합니다.
     */
    private String serialize(RelayRoomState roomState) {
        try {
            return objectMapper.writeValueAsString(roomState);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(ROOM_STATE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    private RelayRoomState deserialize(String roomStateValue) {
        try {
            return objectMapper.readValue(roomStateValue, RelayRoomState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(ROOM_STATE_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
