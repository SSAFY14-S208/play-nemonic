package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.entity.RelayRoomState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRelayRoomRepository implements RelayRoomRepository {

    private static final String ROOM_KEY_PREFIX = "relay:room:";
    private static final String ROOM_STATE_SERIALIZATION_ERROR_MESSAGE = "릴레이 방 상태를 저장할 수 없습니다.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRelayRoomRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByRoomCode(String roomCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createRoomKey(roomCode)));
    }

    @Override
    public void save(RelayRoomState roomState) {
        redisTemplate.opsForValue().set(createRoomKey(roomState.roomCode()), serialize(roomState));
    }

    private String createRoomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
    }

    private String serialize(RelayRoomState roomState) {
        try {
            return objectMapper.writeValueAsString(roomState);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(ROOM_STATE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
