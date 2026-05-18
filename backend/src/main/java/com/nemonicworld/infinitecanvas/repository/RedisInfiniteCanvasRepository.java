package com.nemonicworld.infinitecanvas.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private static final String ROOM_KEY_PREFIX = "infinite-canvas:room:";
    private static final String ACTIVE_CANVAS_INDEX_KEY = "infinite-canvas:rooms:active:created-at";
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
        redisTemplate.opsForValue().set(createRoomKey(canvasState.roomCode()), serialize(canvasState),
            InfiniteCanvasRepository.CANVAS_STATE_TTL);
        syncActiveCanvasIndex(canvasState);
    }

    @Override
    public boolean saveIfUnchanged(InfiniteCanvasState expectedCanvasState, InfiniteCanvasState updatedCanvasState) {
        String roomKey = createRoomKey(expectedCanvasState.roomCode());

        Boolean updated = redisTemplate.execute(new SessionCallback<>() {

            @Override
            public <K, V> Boolean execute(RedisOperations<K, V> operations) throws DataAccessException {
                RedisOperations<String, String> stringOperations = castToStringOperations(operations);

                stringOperations.watch(roomKey);
                String currentCanvasStateValue = stringOperations.opsForValue().get(roomKey);

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
                stringOperations.opsForValue().set(roomKey, serialize(updatedCanvasState),
                    InfiniteCanvasRepository.CANVAS_STATE_TTL);
                syncActiveCanvasIndex(stringOperations, updatedCanvasState);
                List<Object> results = stringOperations.exec();

                return results != null;
            }
        });

        return Boolean.TRUE.equals(updated);
    }

    @Override
    public Optional<InfiniteCanvasState> findByRoomCode(String roomCode) {
        String canvasStateValue = redisTemplate.opsForValue().get(createRoomKey(roomCode));
        if (!StringUtils.hasText(canvasStateValue)) {
            return Optional.empty();
        }

        return Optional.of(deserialize(canvasStateValue));
    }

    @Override
    public InfiniteCanvasActiveCanvasPage findActiveCanvases(int page, int size) {
        long startedNanos = System.nanoTime();
        if (isActiveCanvasIndexEmpty()) {
            return findActiveCanvasesByScanFallback(page, size, startedNanos);
        }

        return findActiveCanvasesByIndex(page, size, startedNanos);
    }

    private InfiniteCanvasActiveCanvasPage findActiveCanvasesByIndex(int page, int size, long startedNanos) {
        long offset = (long) page * size;
        long nextOffset = offset;
        List<InfiniteCanvasState> canvases = new ArrayList<>();
        Set<String> staleRoomCodes = new LinkedHashSet<>();
        int fetchedRoomCount = 0;

        while (canvases.size() < size) {
            long endOffset = nextOffset + size - canvases.size() - 1;
            Set<String> roomCodes = redisTemplate.opsForZSet().reverseRange(ACTIVE_CANVAS_INDEX_KEY, nextOffset,
                endOffset);
            if (roomCodes == null || roomCodes.isEmpty()) {
                break;
            }

            fetchedRoomCount += roomCodes.size();
            nextOffset += roomCodes.size();
            for (String roomCode : roomCodes) {
                Optional<InfiniteCanvasState> canvasState = findByRoomCode(roomCode);
                if (canvasState.isEmpty() || canvasState.get().status() == InfiniteCanvasStatus.CLOSED) {
                    staleRoomCodes.add(roomCode);
                    continue;
                }

                canvases.add(canvasState.get());
                if (canvases.size() == size) {
                    break;
                }
            }

            if (roomCodes.size() < size) {
                break;
            }
        }

        removeActiveCanvasIndexes(staleRoomCodes);
        Long totalElements = redisTemplate.opsForZSet().zCard(ACTIVE_CANVAS_INDEX_KEY);
        log.debug(
            "infinite canvas indexed lookup completed. page={} size={} fetched_room_count={} "
                + "stale_room_count={} matched_canvas_count={} duration_ms={}",
            page, size, fetchedRoomCount, staleRoomCodes.size(), canvases.size(),
            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        return new InfiniteCanvasActiveCanvasPage(canvases, totalElements == null ? 0L : totalElements);
    }

    private InfiniteCanvasActiveCanvasPage findActiveCanvasesByScanFallback(int page, int size, long startedNanos) {
        List<InfiniteCanvasState> canvases = findAllActiveCanvases();
        canvases.forEach(this::syncActiveCanvasIndex);
        List<InfiniteCanvasState> sortedCanvases = canvases.stream()
            .sorted(java.util.Comparator
                .comparing(InfiniteCanvasState::createdAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(InfiniteCanvasState::roomCode,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();
        int fromIndex = Math.min(page * size, sortedCanvases.size());
        int toIndex = Math.min(fromIndex + size, sortedCanvases.size());

        log.debug(
            "infinite canvas scan fallback lookup completed. page={} size={} matched_canvas_count={} duration_ms={}",
            page, size, sortedCanvases.size(), Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        return new InfiniteCanvasActiveCanvasPage(sortedCanvases.subList(fromIndex, toIndex), sortedCanvases.size());
    }

    @Override
    public List<InfiniteCanvasState> findAllActiveCanvases() {
        long startedNanos = System.nanoTime();
        int scannedKeyCount = 0;
        List<InfiniteCanvasState> canvases = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(ROOM_KEY_PREFIX + "*").count(200).build();

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
    public void delete(String roomCode) {
        redisTemplate.delete(createRoomKey(roomCode));
        removeActiveCanvasIndex(roomCode);
    }

    @SuppressWarnings("unchecked")
    private <K, V> RedisOperations<String, String> castToStringOperations(RedisOperations<K, V> operations) {
        return (RedisOperations<String, String>) operations;
    }

    private String createRoomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
    }

    private void syncActiveCanvasIndex(InfiniteCanvasState canvasState) {
        syncActiveCanvasIndex(redisTemplate, canvasState);
    }

    private boolean isActiveCanvasIndexEmpty() {
        Long indexedCanvasCount = redisTemplate.opsForZSet().zCard(ACTIVE_CANVAS_INDEX_KEY);
        return indexedCanvasCount == null || indexedCanvasCount == 0;
    }

    private void syncActiveCanvasIndex(RedisOperations<String, String> operations, InfiniteCanvasState canvasState) {
        if (canvasState.status() == InfiniteCanvasStatus.CLOSED) {
            operations.opsForZSet().remove(ACTIVE_CANVAS_INDEX_KEY, canvasState.roomCode());
            return;
        }

        operations.opsForZSet().add(ACTIVE_CANVAS_INDEX_KEY, canvasState.roomCode(),
            toCreatedAtScore(canvasState.createdAt()));
    }

    private void removeActiveCanvasIndex(String roomCode) {
        if (StringUtils.hasText(roomCode)) {
            redisTemplate.opsForZSet().remove(ACTIVE_CANVAS_INDEX_KEY, roomCode);
        }
    }

    private void removeActiveCanvasIndexes(Set<String> roomCodes) {
        if (!roomCodes.isEmpty()) {
            redisTemplate.opsForZSet().remove(ACTIVE_CANVAS_INDEX_KEY, roomCodes.toArray());
        }
    }

    private double toCreatedAtScore(LocalDateTime createdAt) {
        LocalDateTime safeCreatedAt = createdAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : createdAt;
        return safeCreatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
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
