package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRelayRoomTimeUpNotificationRepository implements RelayRoomTimeUpNotificationRepository {

    private static final String TIME_UP_NOTIFICATION_KEY_FORMAT = "relay:room-time-up-notified:%s:%s:%s";

    private final StringRedisTemplate redisTemplate;

    public RedisRelayRoomTimeUpNotificationRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean markPartTimeUpNotified(String roomCode, RelayDrawingPart part, LocalDateTime partDeadlineAt,
        Duration ttl) {
        Boolean marked = redisTemplate.opsForValue()
            .setIfAbsent(createTimeUpNotificationKey(roomCode, part, partDeadlineAt), "notified", ttl);

        return Boolean.TRUE.equals(marked);
    }

    private String createTimeUpNotificationKey(String roomCode, RelayDrawingPart part, LocalDateTime partDeadlineAt) {
        return TIME_UP_NOTIFICATION_KEY_FORMAT.formatted(roomCode, part, partDeadlineAt);
    }
}
