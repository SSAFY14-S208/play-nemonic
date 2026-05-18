package com.nemonicworld.infinitecanvas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ListOperations;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisInfiniteCanvasRepositoryTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final String ROOM_KEY = "infinite-canvas:room:" + ROOM_CODE;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private StringRedisTemplate redisTemplate;
    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private ListOperations<String, String> listOperations;
    private RedisInfiniteCanvasRepository repository;

    @BeforeEach
    void prepare() {
        redisTemplate = mock(StringRedisTemplate.class);
        redisOperations = createRedisOperationsMock();
        valueOperations = createValueOperationsMock();
        zSetOperations = createZSetOperationsMock();
        listOperations = createListOperationsMock();
        repository = new RedisInfiniteCanvasRepository(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(redisOperations.opsForValue()).willReturn(valueOperations);
        given(redisOperations.opsForZSet()).willReturn(zSetOperations);
        given(redisOperations.opsForList()).willReturn(listOperations);
        given(redisOperations.exec()).willReturn(List.of("OK"));
        given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);

            return callback.execute(redisOperations);
        });
    }

    @Test
    void saveStoresCanvasStateWithTtl() throws Exception {
        InfiniteCanvasState canvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 0L,
            participant(UUID.randomUUID(), "Mango"));

        repository.save(canvasState);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(ROOM_KEY), jsonCaptor.capture(), eq(InfiniteCanvasRepository.CANVAS_STATE_TTL));
        assertThat(deserialize(jsonCaptor.getValue()).operations()).isEmpty();
        verify(redisTemplate).delete("infinite-canvas:room-operations:" + ROOM_CODE);
        verify(zSetOperations).add(eq("infinite-canvas:rooms:active:created-at"), eq(ROOM_CODE), any(Double.class));
    }

    @Test
    void saveIfUnchangedStoresUpdatedCanvasWhenCurrentStateMatchesExpectedState() throws Exception {
        InfiniteCanvasState expectedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 0L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasState updatedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 1L,
            expectedCanvasState.participants().getFirst(), participant(UUID.randomUUID(), "Peach"));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(expectedCanvasState));

        boolean saved = repository.saveIfUnchanged(expectedCanvasState, updatedCanvasState);

        assertThat(saved).isTrue();
        verify(redisOperations).watch(ROOM_KEY);
        verify(redisOperations).multi();
        verify(redisOperations).exec();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(ROOM_KEY), jsonCaptor.capture(), eq(InfiniteCanvasRepository.CANVAS_STATE_TTL));
        assertThat(deserialize(jsonCaptor.getValue()).operations()).isEmpty();
        verify(zSetOperations).add(eq("infinite-canvas:rooms:active:created-at"), eq(ROOM_CODE), any(Double.class));
    }

    @Test
    void saveIfUnchangedAndAppendOperationsStoresStateWithoutOperationsAndAppendsOperationLog() throws Exception {
        InfiniteCanvasState expectedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 0L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasOperation acceptedOperation = operation("op-1", "client-op-1", "element-1", 1L);
        InfiniteCanvasState updatedCanvasState = new InfiniteCanvasState(expectedCanvasState.roomCode(),
            expectedCanvasState.status(), expectedCanvasState.hostUserUuid(), expectedCanvasState.participants(),
            expectedCanvasState.elements(), List.of(acceptedOperation), expectedCanvasState.locks(),
            expectedCanvasState.cursors(), expectedCanvasState.viewport(), expectedCanvasState.maxParticipants(), 1L,
            expectedCanvasState.createdAt(), expectedCanvasState.updatedAt(), expectedCanvasState.closedAt());
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(expectedCanvasState));

        boolean saved = repository.saveIfUnchangedAndAppendOperations(expectedCanvasState, updatedCanvasState,
            List.of(acceptedOperation));

        assertThat(saved).isTrue();
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(ROOM_KEY), jsonCaptor.capture(), eq(InfiniteCanvasRepository.CANVAS_STATE_TTL));
        assertThat(deserialize(jsonCaptor.getValue()).operations()).isEmpty();
        verify(listOperations).rightPushAll(eq("infinite-canvas:room-operations:" + ROOM_CODE), any(List.class));
        verify(listOperations).trim("infinite-canvas:room-operations:" + ROOM_CODE, -200, -1);
        verify(redisOperations).expire("infinite-canvas:room-operations:" + ROOM_CODE,
            InfiniteCanvasRepository.CANVAS_STATE_TTL);
    }

    @Test
    void saveIfUnchangedRemovesActiveIndexWhenCanvasIsClosed() throws Exception {
        InfiniteCanvasState expectedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 0L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasState closedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.CLOSED, 1L,
            expectedCanvasState.participants().getFirst());
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(expectedCanvasState));

        boolean saved = repository.saveIfUnchanged(expectedCanvasState, closedCanvasState);

        assertThat(saved).isTrue();
        verify(zSetOperations).remove("infinite-canvas:rooms:active:created-at", ROOM_CODE);
    }

    @Test
    void saveIfUnchangedReturnsFalseWithoutSavingWhenCurrentStateDiffersFromExpectedState() throws Exception {
        InfiniteCanvasState expectedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 0L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasState changedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 1L,
            expectedCanvasState.participants().getFirst(), participant(UUID.randomUUID(), "Grape"));
        InfiniteCanvasState updatedCanvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 2L,
            expectedCanvasState.participants().getFirst(), participant(UUID.randomUUID(), "Peach"));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(changedCanvasState));

        boolean saved = repository.saveIfUnchanged(expectedCanvasState, updatedCanvasState);

        assertThat(saved).isFalse();
        verify(redisOperations).watch(ROOM_KEY);
        verify(redisOperations).unwatch();
        verify(redisOperations, never()).multi();
        verify(valueOperations, never()).set(eq(ROOM_KEY), any(String.class),
            eq(InfiniteCanvasRepository.CANVAS_STATE_TTL));
    }

    @Test
    void findByRoomCodeRestoresSerializedState() throws Exception {
        InfiniteCanvasState canvasState = canvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, 3L,
            participant(UUID.randomUUID(), "Mango"));
        given(valueOperations.get(ROOM_KEY)).willReturn(serialize(canvasState));

        InfiniteCanvasState restoredState = repository.findByRoomCode(ROOM_CODE).orElseThrow();

        assertThat(restoredState).isEqualTo(canvasState);
    }

    @Test
    void findAllActiveCanvasesScansCanvasKeysAndFiltersClosedCanvases() throws Exception {
        InfiniteCanvasState activeCanvas = canvasState("AC3K9N", InfiniteCanvasStatus.ACTIVE, 1L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasState closedCanvas = canvasState("AC3K9P", InfiniteCanvasStatus.CLOSED, 2L,
            participant(UUID.randomUUID(), "Peach"));
        Cursor<String> cursor = createCursorMock();
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, false);
        given(cursor.next()).willReturn("infinite-canvas:room:AC3K9N", "infinite-canvas:room:AC3K9P");
        given(valueOperations.get("infinite-canvas:room:AC3K9N")).willReturn(serialize(activeCanvas));
        given(valueOperations.get("infinite-canvas:room:AC3K9P")).willReturn(serialize(closedCanvas));

        List<InfiniteCanvasState> activeCanvases = repository.findAllActiveCanvases();

        assertThat(activeCanvases).containsExactly(activeCanvas);
        verify(cursor).close();
    }

    @Test
    void findActiveCanvasesReadsOnlyIndexedPageAndRemovesStaleEntries() throws Exception {
        InfiniteCanvasState activeCanvas = canvasState("AC3K9N", InfiniteCanvasStatus.ACTIVE, 1L,
            participant(UUID.randomUUID(), "Mango"));
        InfiniteCanvasState closedCanvas = canvasState("AC3K9P", InfiniteCanvasStatus.CLOSED, 2L,
            participant(UUID.randomUUID(), "Peach"));
        given(zSetOperations.zCard("infinite-canvas:rooms:active:created-at")).willReturn(2L, 1L);
        given(zSetOperations.reverseRange("infinite-canvas:rooms:active:created-at", 0, 1))
            .willReturn(new java.util.LinkedHashSet<>(List.of("AC3K9N", "AC3K9P")));
        given(valueOperations.get("infinite-canvas:room:AC3K9N")).willReturn(serialize(activeCanvas));
        given(valueOperations.get("infinite-canvas:room:AC3K9P")).willReturn(serialize(closedCanvas));

        InfiniteCanvasActiveCanvasPage page = repository.findActiveCanvases(0, 2);

        assertThat(page.items()).containsExactly(activeCanvas);
        assertThat(page.totalElements()).isEqualTo(1L);
        verify(zSetOperations).remove("infinite-canvas:rooms:active:created-at", "AC3K9P");
    }

    @Test
    void findActiveCanvasesFallsBackToScanAndBackfillsIndexWhenIndexIsEmpty() throws Exception {
        InfiniteCanvasState activeCanvas = canvasState("AC3K9N", InfiniteCanvasStatus.ACTIVE, 1L,
            participant(UUID.randomUUID(), "Mango"));
        Cursor<String> cursor = createCursorMock();
        given(zSetOperations.zCard("infinite-canvas:rooms:active:created-at")).willReturn(0L);
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, false);
        given(cursor.next()).willReturn("infinite-canvas:room:AC3K9N");
        given(valueOperations.get("infinite-canvas:room:AC3K9N")).willReturn(serialize(activeCanvas));

        InfiniteCanvasActiveCanvasPage page = repository.findActiveCanvases(0, 20);

        assertThat(page.items()).containsExactly(activeCanvas);
        assertThat(page.totalElements()).isEqualTo(1L);
        verify(zSetOperations).add(eq("infinite-canvas:rooms:active:created-at"), eq(activeCanvas.roomCode()),
            any(Double.class));
    }

    private InfiniteCanvasState canvasState(String roomCode, InfiniteCanvasStatus status, long revision,
        InfiniteCanvasParticipant... participants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new InfiniteCanvasState(roomCode, status, participants[0].userUuid(), List.of(participants),
            List.of(element()), List.of(), Map.of(), Map.of(), viewport(), 6, revision, now.minusMinutes(1), now,
            status == InfiniteCanvasStatus.CLOSED ? now : null);
    }

    private InfiniteCanvasParticipant participant(UUID userUuid, String nickname) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new InfiniteCanvasParticipant(userUuid.toString(), nickname, "#72DDF7", null, true, true,
            now.minusMinutes(1), now, now);
    }

    private JsonNode viewport() {
        return objectMapper.createObjectNode().put("x", 0).put("y", 0).put("zoom", 1.0);
    }

    private JsonNode element() {
        return objectMapper.createObjectNode().put("id", "element-1").put("type", "brush");
    }

    private InfiniteCanvasOperation operation(String operationId, String clientOperationId, String elementId,
        long revision) {
        return new InfiniteCanvasOperation(operationId, clientOperationId, InfiniteCanvasOperationType.UPSERT_ELEMENT,
            elementId, element(), null, UUID.randomUUID().toString(), revision,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    private String serialize(InfiniteCanvasState canvasState) throws Exception {
        return objectMapper.writeValueAsString(canvasState);
    }

    private InfiniteCanvasState deserialize(String value) throws Exception {
        return objectMapper.readValue(value, InfiniteCanvasState.class);
    }

    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }

    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    private ZSetOperations<String, String> createZSetOperationsMock() {
        return (ZSetOperations<String, String>) mock(ZSetOperations.class);
    }

    private ListOperations<String, String> createListOperationsMock() {
        return (ListOperations<String, String>) mock(ListOperations.class);
    }

    private Cursor<String> createCursorMock() {
        return (Cursor<String>) mock(Cursor.class);
    }
}
