package com.nemonicworld.relay.repository;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRelayFinalizationRetryRepository implements RelayFinalizationRetryRepository {

    private static final String FINALIZATION_RETRY_KEY_PREFIX = "relay:room-finalization-retry:";

    private final StringRedisTemplate redisTemplate;

    public RedisRelayFinalizationRetryRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int incrementFailureCount(String roomCode, Duration ttl) {
        Long failureCount = redisTemplate.opsForValue().increment(createFinalizationRetryKey(roomCode));
        redisTemplate.expire(createFinalizationRetryKey(roomCode), ttl);

        return failureCount == null ? 0 : failureCount.intValue();
    }

    @Override
    public void clearFailureCount(String roomCode) {
        redisTemplate.delete(createFinalizationRetryKey(roomCode));
    }

    private String createFinalizationRetryKey(String roomCode) {
        return FINALIZATION_RETRY_KEY_PREFIX + roomCode;
    }
}
