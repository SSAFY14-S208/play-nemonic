package com.nemonicworld.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.service.finalization.RelayFinalizationAttempt;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRelayFinalizationAttemptRepositoryTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String ATTEMPT_ID = "attempt-1";
    private static final String ATTEMPT_KEY = "relay:room-finalization-attempt:AB3K9Q:attempt-1";
    private static final String OBJECT_KEY = "relay/results/artifact-id/original.png";
    private static final Duration TTL = Duration.ofHours(24);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisRelayFinalizationAttemptRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = createValueOperationsMock();
        repository = new RedisRelayFinalizationAttemptRepository(redisTemplate, objectMapper);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void saveStoresAttemptWithTtl() throws Exception {
        RelayFinalizationAttempt attempt = new RelayFinalizationAttempt(ROOM_CODE, ATTEMPT_ID, List.of(OBJECT_KEY),
            LocalDateTime.of(2026, 5, 11, 22, 0));

        repository.save(attempt, TTL);

        verify(valueOperations).set(eq(ATTEMPT_KEY), any(String.class), eq(TTL));
    }

    @Test
    void containsObjectKeyScansAttemptMarkers() throws Exception {
        RelayFinalizationAttempt attempt = new RelayFinalizationAttempt(ROOM_CODE, ATTEMPT_ID, List.of(OBJECT_KEY),
            LocalDateTime.of(2026, 5, 11, 22, 0));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, false);
        given(cursor.next()).willReturn(ATTEMPT_KEY);
        given(valueOperations.get(ATTEMPT_KEY)).willReturn(objectMapper.writeValueAsString(attempt));

        boolean contains = repository.containsObjectKey(OBJECT_KEY, 10);

        assertThat(contains).isTrue();
        verify(cursor).close();
    }

    @Test
    void clearDeletesAttemptMarker() {
        repository.clear(ROOM_CODE, ATTEMPT_ID);

        verify(redisTemplate).delete(ATTEMPT_KEY);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> createCursorMock() {
        return (Cursor<String>) mock(Cursor.class);
    }
}
