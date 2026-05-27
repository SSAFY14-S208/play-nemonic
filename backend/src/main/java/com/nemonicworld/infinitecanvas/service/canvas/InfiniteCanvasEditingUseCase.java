package com.nemonicworld.infinitecanvas.service.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasRevisionConflictResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.exception.InfiniteCanvasRevisionConflictException;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasEditingUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String INVALID_CURSOR_MESSAGE = "커서 좌표 형식이 올바르지 않습니다.";
    private static final String INVALID_ELEMENTS_MESSAGE = "캔버스 요소 목록 형식이 올바르지 않습니다.";
    private static final String INVALID_OPERATIONS_MESSAGE = "캔버스 편집 연산 목록 형식이 올바르지 않습니다.";
    private static final String BASE_REVISION_REQUIRED_MESSAGE = "baseRevision을 지정해주세요.";
    private static final String STALE_REVISION_MESSAGE = "캔버스 revision이 최신이 아닙니다. 서버 상태를 다시 동기화해주세요.";
    private static final String SNAPSHOT_HOST_MESSAGE = "캔버스 방장만 전체 스냅샷을 교체할 수 있습니다.";
    private static final String LOCK_CONFLICT_MESSAGE = "다른 참여자가 해당 요소를 편집 중입니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final int UPDATE_MAX_RETRIES = 8;
    private static final int MAX_ELEMENTS = 5000;
    private static final int MAX_OPERATIONS_PER_MESSAGE = 100;
    private static final int RECENT_OPERATION_LIMIT = 200;

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;

    public InfiniteCanvasEditingUseCase(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InfiniteCanvasRepository infiniteCanvasRepository) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String roomCode,
        InfiniteCanvasSnapshotRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        List<JsonNode> elements = normalizeElements(request == null ? null : request.elements());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            requireParticipant(state, userUuid);
            requireCanvasHost(state, userUuid);
            requireFreshRevision(request == null ? null : request.baseRevision(), state);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            requireNoForeignLocks(locks, userUuid);
            InfiniteCanvasState updatedState = copyState(state, state.participants(), elements, state.operations(),
                locks, state.cursors(), request == null ? state.viewport() : request.viewport(), state.revision() + 1,
                now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String roomCode,
        InfiniteCanvasCursorRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        if (request == null || request.x() == null || request.y() == null || !isFinite(request.x())
            || !isFinite(request.y()) || (request.zoom() != null && !isFinite(request.zoom()))) {
            throw new BadRequestException(INVALID_CURSOR_MESSAGE);
        }

        InfiniteCanvasState state = findActiveState(normalizedRoomCode);
        requireParticipant(state, userUuid);
        InfiniteCanvasCursor cursor = new InfiniteCanvasCursor(userUuid, request.x(), request.y(), request.zoom(),
            request.payload(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return new InfiniteCanvasCursorResponse(normalizedRoomCode, cursor);
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String roomCode,
        InfiniteCanvasOpsRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        List<InfiniteCanvasOperationRequest> requestedOperations = normalizeOperationRequests(request);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            requireParticipant(state, userUuid);
            List<InfiniteCanvasOperationRequest> pendingOperationRequests = requestedOperations.stream()
                .filter(operationRequest -> !isAlreadyApplied(state, operationRequest, userUuid)).toList();
            if (pendingOperationRequests.isEmpty()) {
                return new InfiniteCanvasOpsAppliedResponse(normalizedRoomCode, state.revision(),
                    state.elements().size(), List.of());
            }
            requireMergeableRevision(request == null ? null : request.baseRevision(), state, pendingOperationRequests);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            CanvasElementBatch elements = new CanvasElementBatch(state.elements());
            List<InfiniteCanvasOperation> acceptedOperations = new ArrayList<>();
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            long revision = state.revision();

            for (InfiniteCanvasOperationRequest operationRequest : pendingOperationRequests) {
                validateOperationLock(locks, operationRequest, userUuid);
                revision++;
                InfiniteCanvasOperation operation = createOperation(operationRequest, userUuid, revision, now);
                applyOperation(elements, locks, operation);
                acceptedOperations.add(operation);
            }

            List<JsonNode> updatedElements = elements.toList();
            InfiniteCanvasState updatedState = copyState(state, state.participants(), updatedElements,
                appendRecentOperations(state.operations(), acceptedOperations), locks, state.cursors(),
                state.viewport(), revision, now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchangedAndAppendOperations(state, updatedState, acceptedOperations)) {
                return new InfiniteCanvasOpsAppliedResponse(normalizedRoomCode, revision, updatedElements.size(),
                    acceptedOperations);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    private InfiniteCanvasState findActiveState(String roomCode) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
        }
    }

    private void requireCanvasHost(InfiniteCanvasState state, String userUuid) {
        if (!userUuid.equals(state.hostUserUuid())) {
            throw new ConflictException(SNAPSHOT_HOST_MESSAGE);
        }
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    private List<JsonNode> normalizeElements(List<JsonNode> elements) {
        if (elements == null) {
            return List.of();
        }
        if (elements.size() > MAX_ELEMENTS) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }
        if (elements.stream().anyMatch(element -> element == null || element.isNull())) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }

        return List.copyOf(elements);
    }

    private List<InfiniteCanvasOperationRequest> normalizeOperationRequests(InfiniteCanvasOpsRequest request) {
        List<InfiniteCanvasOperationRequest> operations = request == null ? null : request.operations();
        if (operations == null || operations.isEmpty() || operations.size() > MAX_OPERATIONS_PER_MESSAGE
            || operations.stream().anyMatch(operation -> operation == null || operation.operationType() == null
                || !StringUtils.hasText(operation.clientOperationId()))) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        return List.copyOf(operations);
    }

    private void requireFreshRevision(Long baseRevision, InfiniteCanvasState state) {
        if (baseRevision == null) {
            throw new BadRequestException(BASE_REVISION_REQUIRED_MESSAGE);
        }
        if (baseRevision.longValue() != state.revision()) {
            throw new InfiniteCanvasRevisionConflictException(STALE_REVISION_MESSAGE,
                revisionConflictResponse(baseRevision, state));
        }
    }

    private void requireMergeableRevision(Long baseRevision, InfiniteCanvasState state,
        List<InfiniteCanvasOperationRequest> pendingOperationRequests) {
        if (baseRevision == null) {
            throw new BadRequestException(BASE_REVISION_REQUIRED_MESSAGE);
        }

        long requestedRevision = baseRevision.longValue();
        if (requestedRevision == state.revision()) {
            return;
        }

        List<InfiniteCanvasOperation> missingOperations = missingOperations(requestedRevision, state);
        boolean canRecoverWithDelta = canRecoverWithDelta(requestedRevision, state.revision(), missingOperations);
        if (!canRecoverWithDelta || hasOperationConflict(pendingOperationRequests, missingOperations)) {
            throw new InfiniteCanvasRevisionConflictException(STALE_REVISION_MESSAGE,
                revisionConflictResponse(requestedRevision, state));
        }
    }

    private InfiniteCanvasRevisionConflictResponse revisionConflictResponse(long baseRevision,
        InfiniteCanvasState state) {
        List<InfiniteCanvasOperation> missingOperations = missingOperations(baseRevision, state);
        boolean fullStateRequired = !canRecoverWithDelta(baseRevision, state.revision(), missingOperations);

        return new InfiniteCanvasRevisionConflictResponse(state.roomCode(), baseRevision, state.revision(),
            fullStateRequired ? List.of() : missingOperations, fullStateRequired);
    }

    private List<InfiniteCanvasOperation> missingOperations(long baseRevision, InfiniteCanvasState state) {
        if (baseRevision >= state.revision()) {
            return List.of();
        }

        return state.operations().stream()
            .filter(operation -> operation.revision() > baseRevision && operation.revision() <= state.revision())
            .toList();
    }

    private boolean canRecoverWithDelta(long baseRevision, long latestRevision,
        List<InfiniteCanvasOperation> missingOperations) {
        if (baseRevision >= latestRevision) {
            return false;
        }
        if (missingOperations.size() != latestRevision - baseRevision) {
            return false;
        }

        long expectedRevision = baseRevision + 1;
        for (InfiniteCanvasOperation operation : missingOperations) {
            if (operation.revision() != expectedRevision) {
                return false;
            }
            expectedRevision++;
        }

        return true;
    }

    private boolean hasOperationConflict(List<InfiniteCanvasOperationRequest> pendingOperationRequests,
        List<InfiniteCanvasOperation> missingOperations) {
        if (pendingOperationRequests.stream().anyMatch(
            operationRequest -> operationRequest.operationType() == InfiniteCanvasOperationType.CLEAR_CANVAS)) {
            return true;
        }

        Set<String> requestedElementIds = new HashSet<>();
        for (InfiniteCanvasOperationRequest operationRequest : pendingOperationRequests) {
            String elementId = resolveOperationElementId(operationRequest);
            if (StringUtils.hasText(elementId)) {
                requestedElementIds.add(elementId);
            }
        }

        for (InfiniteCanvasOperation missingOperation : missingOperations) {
            if (missingOperation.operationType() == InfiniteCanvasOperationType.CLEAR_CANVAS) {
                return true;
            }
            if (StringUtils.hasText(missingOperation.elementId())
                && requestedElementIds.contains(missingOperation.elementId())) {
                return true;
            }
        }

        return false;
    }

    private InfiniteCanvasOperation createOperation(InfiniteCanvasOperationRequest request, String userUuid,
        long revision, LocalDateTime now) {
        String operationId = StringUtils.hasText(request.operationId())
            ? request.operationId().trim()
            : UUID.randomUUID().toString();
        JsonNode element = resolveOperationElement(request);
        String elementId = StringUtils.hasText(request.elementId())
            ? request.elementId().trim()
            : extractElementId(element);

        return new InfiniteCanvasOperation(operationId, request.clientOperationId().trim(), request.operationType(),
            elementId, element, request.payload(), userUuid, revision, now);
    }

    private JsonNode resolveOperationElement(InfiniteCanvasOperationRequest request) {
        if (request.element() != null && !request.element().isNull()) {
            return request.element();
        }
        if (request.payload() != null && request.payload().has("element")) {
            return request.payload().get("element");
        }
        return null;
    }

    private boolean isAlreadyApplied(InfiniteCanvasState state, InfiniteCanvasOperationRequest operationRequest,
        String userUuid) {
        if (operationRequest == null || !StringUtils.hasText(operationRequest.clientOperationId())) {
            return false;
        }

        String clientOperationId = operationRequest.clientOperationId().trim();
        return state.operations().stream().anyMatch(operation -> clientOperationId.equals(operation.clientOperationId())
            && userUuid.equals(operation.userUuid()));
    }

    private void validateOperationLock(Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperationRequest operationRequest, String userUuid) {
        InfiniteCanvasOperationType operationType = operationRequest.operationType();
        if (operationType == InfiniteCanvasOperationType.CLEAR_CANVAS) {
            requireNoForeignLocks(locks, userUuid);
            return;
        }

        String elementId = resolveOperationElementId(operationRequest);
        if (operationType == InfiniteCanvasOperationType.DELETE_ELEMENT && !StringUtils.hasText(elementId)) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        requireElementEditable(locks, elementId, userUuid);
    }

    private void requireElementEditable(Map<String, InfiniteCanvasLock> locks, String elementId, String userUuid) {
        if (!StringUtils.hasText(elementId)) {
            return;
        }

        InfiniteCanvasLock lock = locks.get(elementId);
        if (lock != null && !userUuid.equals(lock.userUuid())) {
            throw new ConflictException(LOCK_CONFLICT_MESSAGE);
        }
    }

    private void requireNoForeignLocks(Map<String, InfiniteCanvasLock> locks, String userUuid) {
        boolean hasForeignLock = locks.values().stream()
            .anyMatch(lock -> lock != null && !userUuid.equals(lock.userUuid()));
        if (hasForeignLock) {
            throw new ConflictException(LOCK_CONFLICT_MESSAGE);
        }
    }

    private String resolveOperationElementId(InfiniteCanvasOperationRequest operationRequest) {
        if (StringUtils.hasText(operationRequest.elementId())) {
            return operationRequest.elementId().trim();
        }

        return extractElementId(resolveOperationElement(operationRequest));
    }

    private void applyOperation(CanvasElementBatch elements, Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperation operation) {
        switch (operation.operationType()) {
            case CLEAR_CANVAS -> {
                elements.clear();
                locks.clear();
            }
            case DELETE_ELEMENT -> {
                removeElement(elements, operation.elementId());
                if (StringUtils.hasText(operation.elementId())) {
                    locks.remove(operation.elementId());
                }
            }
            case CREATE_ELEMENT, UPDATE_ELEMENT, UPSERT_ELEMENT ->
                upsertElement(elements, operation.elementId(), operation.element());
        }
    }

    private void upsertElement(CanvasElementBatch elements, String elementId, JsonNode element) {
        if (element == null || element.isNull()) {
            return;
        }

        String resolvedElementId = StringUtils.hasText(elementId) ? elementId : extractElementId(element);
        if (!StringUtils.hasText(resolvedElementId)) {
            elements.add(element);
            return;
        }

        elements.upsert(resolvedElementId, element);
    }

    private void removeElement(CanvasElementBatch elements, String elementId) {
        if (!StringUtils.hasText(elementId)) {
            return;
        }

        elements.remove(elementId);
    }

    private String extractElementId(JsonNode element) {
        if (element == null || !element.isObject()) {
            return null;
        }
        if (StringUtils.hasText(element.path("id").asText(null))) {
            return element.path("id").asText();
        }
        if (StringUtils.hasText(element.path("elementId").asText(null))) {
            return element.path("elementId").asText();
        }
        return null;
    }

    private List<InfiniteCanvasOperation> appendRecentOperations(List<InfiniteCanvasOperation> currentOperations,
        List<InfiniteCanvasOperation> acceptedOperations) {
        List<InfiniteCanvasOperation> operations = new ArrayList<>(currentOperations);
        operations.addAll(acceptedOperations);
        if (operations.size() <= RECENT_OPERATION_LIMIT) {
            return operations;
        }

        return operations.subList(operations.size() - RECENT_OPERATION_LIMIT, operations.size());
    }

    private boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private final class CanvasElementBatch {

        private final LinkedHashMap<String, JsonNode> elementsBySlot = new LinkedHashMap<>();
        private final Map<String, List<String>> slotKeysByElementId = new LinkedHashMap<>();
        private int nextSlotIndex;

        private CanvasElementBatch(List<JsonNode> elements) {
            for (JsonNode element : elements) {
                add(element);
            }
        }

        private void add(JsonNode element) {
            append(element, extractElementId(element));
        }

        private void upsert(String elementId, JsonNode element) {
            remove(elementId);
            append(element, elementId);
        }

        private void remove(String elementId) {
            List<String> slotKeys = slotKeysByElementId.remove(elementId);
            if (slotKeys == null) {
                return;
            }

            for (String slotKey : slotKeys) {
                elementsBySlot.remove(slotKey);
            }
        }

        private void clear() {
            elementsBySlot.clear();
            slotKeysByElementId.clear();
        }

        private List<JsonNode> toList() {
            return List.copyOf(elementsBySlot.values());
        }

        private void append(JsonNode element, String elementId) {
            String slotKey = nextSlotKey(elementId);
            elementsBySlot.put(slotKey, element);
            if (StringUtils.hasText(elementId)) {
                slotKeysByElementId.computeIfAbsent(elementId, unused -> new ArrayList<>()).add(slotKey);
            }
        }

        private String nextSlotKey(String elementId) {
            String prefix = StringUtils.hasText(elementId) ? "element:" + elementId : "anonymous";
            return prefix + ":" + nextSlotIndex++;
        }
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        List<JsonNode> elements, List<InfiniteCanvasOperation> operations, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, JsonNode viewport, long revision, LocalDateTime updatedAt,
        LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.roomCode(), state.status(), state.hostUserUuid(), participants, elements,
            operations, locks, cursors, viewport, state.maxParticipants(), revision, state.createdAt(), updatedAt,
            closedAt);
    }

    private Map<String, InfiniteCanvasLock> removeExpiredLocks(Map<String, InfiniteCanvasLock> locks,
        LocalDateTime now) {
        Map<String, InfiniteCanvasLock> activeLocks = new LinkedHashMap<>();
        locks.forEach((elementId, lock) -> {
            if (lock != null && !lock.isExpired(now)) {
                activeLocks.put(elementId, lock);
            }
        });

        return activeLocks;
    }
}
