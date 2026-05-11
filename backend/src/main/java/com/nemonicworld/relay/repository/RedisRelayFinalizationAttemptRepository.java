package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.service.finalization.RelayFinalizationAttempt;
import java.time.Duration;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RedisRelayFinalizationAttemptRepository implements RelayFinalizationAttemptRepository {

    private static final String FINALIZATION_ATTEMPT_KEY_PREFIX = "relay:room-finalization-attempt:";
    private static final String ATTEMPT_SERIALIZATION_ERROR_MESSAGE = "릴레이 최종화 attempt 정보를 저장할 수 없습니다.";
    private static final String ATTEMPT_DESERIALIZATION_ERROR_MESSAGE = "릴레이 최종화 attempt 정보를 읽을 수 없습니다.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRelayFinalizationAttemptRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(RelayFinalizationAttempt attempt, Duration ttl) {
        redisTemplate.opsForValue().set(createAttemptKey(attempt.roomCode(), attempt.attemptId()), serialize(attempt),
            ttl);
    }

    @Override
    public void clear(String roomCode, String attemptId) {
        redisTemplate.delete(createAttemptKey(roomCode, attemptId));
    }

    @Override
    public boolean containsObjectKey(String objectKey, int scanLimit) {
        if (!StringUtils.hasText(objectKey) || scanLimit <= 0) {
            return false;
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(FINALIZATION_ATTEMPT_KEY_PREFIX + "*")
            .count(scanLimit).build();
        int scannedCount = 0;
        try (Cursor<String> attemptKeys = redisTemplate.scan(scanOptions)) {
            while (attemptKeys.hasNext() && scannedCount < scanLimit) {
                scannedCount++;
                String attemptValue = redisTemplate.opsForValue().get(attemptKeys.next());
                if (!StringUtils.hasText(attemptValue)) {
                    continue;
                }

                RelayFinalizationAttempt attempt = deserialize(attemptValue);
                if (attempt.objectKeys().contains(objectKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String createAttemptKey(String roomCode, String attemptId) {
        return FINALIZATION_ATTEMPT_KEY_PREFIX + roomCode + ":" + attemptId;
    }

    private String serialize(RelayFinalizationAttempt attempt) {
        try {
            return objectMapper.writeValueAsString(attempt);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ATTEMPT_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    private RelayFinalizationAttempt deserialize(String attemptValue) {
        try {
            return objectMapper.readValue(attemptValue, RelayFinalizationAttempt.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ATTEMPT_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
