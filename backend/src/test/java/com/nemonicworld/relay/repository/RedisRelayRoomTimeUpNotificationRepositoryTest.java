package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRelayRoomTimeUpNotificationRepositoryTest {

    private static final String ROOM_CODE = "AB3K9Q";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRelayRoomTimeUpNotificationRepository repository;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        repository = new RedisRelayRoomTimeUpNotificationRepository(redisTemplate);
    }

    @Test
    void markPartTimeUpNotifiedStoresSingleUseMarkerWithRoomPartAndDeadline() {
        LocalDateTime partDeadlineAt = LocalDateTime.of(2026, 5, 5, 14, 1, 16);
        Duration ttl = Duration.ofHours(24);
        given(
            valueOperations.setIfAbsent("relay:room-time-up-notified:AB3K9Q:FACE:2026-05-05T14:01:16", "notified", ttl))
            .willReturn(true);

        boolean marked = repository.markPartTimeUpNotified(ROOM_CODE, RelayDrawingPart.FACE, partDeadlineAt, ttl);

        assertThat(marked).isTrue();
    }

    @Test
    void markPartTimeUpNotifiedReturnsFalseWhenMarkerAlreadyExists() {
        LocalDateTime partDeadlineAt = LocalDateTime.of(2026, 5, 5, 14, 1, 16);
        Duration ttl = Duration.ofHours(24);
        given(
            valueOperations.setIfAbsent("relay:room-time-up-notified:AB3K9Q:FACE:2026-05-05T14:01:16", "notified", ttl))
            .willReturn(false);

        boolean marked = repository.markPartTimeUpNotified(ROOM_CODE, RelayDrawingPart.FACE, partDeadlineAt, ttl);

        assertThat(marked).isFalse();
    }
}
