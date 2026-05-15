package com.nemonicworld.infinitecanvas.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RedisInfiniteCanvasRepository implements InfiniteCanvasRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisInfiniteCanvasRepository.class);

    private static final String CANVAS_KEY_PREFIX = "infinite-canvas:canvas:";
    private static final String SERIALIZATION_ERROR_MESSAGE = "무한 캔버스 상태를 저장할 수 없습니다.";
    private static final String DESERIALIZATION_ERROR_MESSAGE = "무한 캔버스 상태를 읽을 수 없습니다.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisInfiniteCanvasRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(InfiniteCanvasState canvasState) {
        redisTemplate.opsForValue().set(createCanvasKey(canvasState.canvasId()), serialize(canvasState),
            InfiniteCanvasRepository.CANVAS_STATE_TTL);
    }

    @Override
    public boolean saveIfUnchanged(InfiniteCanvasState expectedCanvasState, InfiniteCanvasState updatedCanvasState) {
        String canvasKey = createCanvasKey(expectedCanvasState.canvasId());

        Boolean updated = redisTemplate.execute(new SessionCallback<>() {

            @Override
            public <K, V> Boolean execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> stringOperations = castToStringOperations(operations);

                stringOperations.watch(canvasKey);
                String currentCanvasStateValue = stringOperations.opsForValue().get(canvasKey);

                if (!StringUtils.hasText(currentCanvasStateValue)) {
                    stringOperations.unwatch();
                    return false;
                }

                InfiniteCanvasState currentCanvasState = deserialize(currentCanvasStateValue);
                if (!currentCanvasState.equals(expectedCanvasState)) {
                    stringOperations.unwatch();
                    return false;
                }

                stringOperations.multi();
                stringOperations.opsForValue().set(canvasKey, serialize(updatedCanvasState),
                    InfiniteCanvasRepository.CANVAS_STATE_TTL);
                List<Object> results = stringOperations.exec();

                return results != null;
            }
        });

        return Boolean.TRUE.equals(updated);
    }

    @Override
    public Optional<InfiniteCanvasState> findByCanvasId(String canvasId) {
        String canvasStateValue = redisTemplate.opsForValue().get(createCanvasKey(canvasId));
        if (!StringUtils.hasText(canvasStateValue)) {
            return Optional.empty();
        }

        return Optional.of(deserialize(canvasStateValue));
    }

    @Override
    public List<InfiniteCanvasState> findAllActiveCanvases() {
        long startedNanos = System.nanoTime();
        int scannedKeyCount = 0;
        List<InfiniteCanvasState> canvases = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(CANVAS_KEY_PREFIX + "*").count(200).build();

        try (Cursor<String> canvasKeys = redisTemplate.scan(scanOptions)) {
            while (canvasKeys.hasNext()) {
                scannedKeyCount++;
                String canvasStateValue = redisTemplate.opsForValue().get(canvasKeys.next());
                if (!StringUtils.hasText(canvasStateValue)) {
                    continue;
                }

                InfiniteCanvasState canvasState = deserialize(canvasStateValue);
                if (canvasState.status() != InfiniteCanvasStatus.CLOSED) {
                    canvases.add(canvasState);
                }
            }
        }

        log.debug("infinite canvas scan completed. scanned_key_count={} matched_canvas_count={} duration_ms={}",
            scannedKeyCount, canvases.size(), Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        return canvases;
    }

    @Override
    public void delete(String canvasId) {
        redisTemplate.delete(createCanvasKey(canvasId));
    }

    @SuppressWarnings("unchecked")
    private <K, V> RedisOperations<String, String> castToStringOperations(RedisOperations<K, V> operations) {
        return (RedisOperations<String, String>) operations;
    }

    private String createCanvasKey(String canvasId) {
        return CANVAS_KEY_PREFIX + canvasId;
    }

    private String serialize(InfiniteCanvasState canvasState) {
        try {
            return objectMapper.writeValueAsString(canvasState);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(SERIALIZATION_ERROR_MESSAGE);
        }
    }

    private InfiniteCanvasState deserialize(String value) {
        try {
            return objectMapper.readValue(value, InfiniteCanvasState.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(DESERIALIZATION_ERROR_MESSAGE);
        }
    }
}
