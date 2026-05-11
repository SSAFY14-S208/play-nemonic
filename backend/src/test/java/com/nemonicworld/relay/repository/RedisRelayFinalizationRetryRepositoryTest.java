package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRelayFinalizationRetryRepositoryTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String RETRY_KEY = "relay:room-finalization-retry:" + ROOM_CODE;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisRelayFinalizationRetryRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = createValueOperationsMock();
        repository = new RedisRelayFinalizationRetryRepository(redisTemplate);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void incrementFailureCountIncrementsRetryKeyAndRefreshesTtl() {
        Duration ttl = Duration.ofHours(24);
        given(valueOperations.increment(RETRY_KEY)).willReturn(3L);

        int failureCount = repository.incrementFailureCount(ROOM_CODE, ttl);

        assertThat(failureCount).isEqualTo(3);
        verify(valueOperations).increment(RETRY_KEY);
        verify(redisTemplate).expire(RETRY_KEY, ttl);
    }

    @Test
    void clearFailureCountDeletesRetryKey() {
        repository.clearFailureCount(ROOM_CODE);

        verify(redisTemplate).delete(RETRY_KEY);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
