package com.nemonicworld.flipbook.repository;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisFlipbookSubmissionLockRepository implements FlipbookSubmissionLockRepository {

    private static final String SUBMISSION_LOCK_KEY_FORMAT = "flipbook:room:%s:submit-lock:%d:%d:%d:%s";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFlipbookSubmissionLockRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireSubmissionLock(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid,
        String token, Duration ttl) {
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(createSubmissionLockKey(roomCode, flipbookIndex, frameIndex, round, userUuid), token, ttl);

        return Boolean.TRUE.equals(locked);
    }

    @Override
    public boolean isSubmissionLocked(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(createSubmissionLockKey(roomCode, flipbookIndex, frameIndex, round, userUuid)));
    }

    @Override
    public void releaseSubmissionLock(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid,
        String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT,
            List.of(createSubmissionLockKey(roomCode, flipbookIndex, frameIndex, round, userUuid)), token);
    }

    private String createSubmissionLockKey(String roomCode, int flipbookIndex, int frameIndex, int round,
        String userUuid) {
        return SUBMISSION_LOCK_KEY_FORMAT.formatted(roomCode, flipbookIndex, frameIndex, round, userUuid);
    }
}
