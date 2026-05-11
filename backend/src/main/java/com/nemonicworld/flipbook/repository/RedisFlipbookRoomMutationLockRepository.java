package com.nemonicworld.flipbook.repository;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisFlipbookRoomMutationLockRepository implements FlipbookRoomMutationLockRepository {

    private static final String ROOM_MUTATION_LOCK_KEY_FORMAT = "flipbook:room-mutation-lock:%s";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFlipbookRoomMutationLockRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireRoomMutationLock(String roomCode, String token, Duration ttl) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(createRoomMutationLockKey(roomCode), token, ttl);

        return Boolean.TRUE.equals(locked);
    }

    @Override
    public void releaseRoomMutationLock(String roomCode, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(createRoomMutationLockKey(roomCode)), token);
    }

    private String createRoomMutationLockKey(String roomCode) {
        return ROOM_MUTATION_LOCK_KEY_FORMAT.formatted(roomCode);
    }
}
