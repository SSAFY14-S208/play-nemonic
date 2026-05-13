package com.nemonicworld.flipbook.repository;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisFlipbookRoomTimeUpNotificationRepository implements FlipbookRoomTimeUpNotificationRepository {

    private static final String TIME_UP_NOTIFICATION_KEY_FORMAT = "flipbook:room-time-up-notified:%s:%d:%s";

    private final StringRedisTemplate redisTemplate;

    public RedisFlipbookRoomTimeUpNotificationRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean markRoundTimeUpNotified(String roomCode, int round, LocalDateTime roundDeadlineAt, Duration ttl) {
        Boolean marked = redisTemplate.opsForValue()
            .setIfAbsent(createTimeUpNotificationKey(roomCode, round, roundDeadlineAt), "notified", ttl);

        return Boolean.TRUE.equals(marked);
    }

    private String createTimeUpNotificationKey(String roomCode, int round, LocalDateTime roundDeadlineAt) {
        return TIME_UP_NOTIFICATION_KEY_FORMAT.formatted(roomCode, round, roundDeadlineAt);
    }
}
