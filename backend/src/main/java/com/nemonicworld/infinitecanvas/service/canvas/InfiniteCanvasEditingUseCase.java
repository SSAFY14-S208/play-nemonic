package com.nemonicworld.infinitecanvas.service.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InfiniteCanvasEditingUseCase {

    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final int UPDATE_MAX_RETRIES = 8;

    private final AnonymousUserResolver anonymousUserResolver;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasEditValidationSupport validationSupport;
    private final InfiniteCanvasRevisionSupport revisionSupport;
    private final InfiniteCanvasEditLockSupport lockSupport;
    private final InfiniteCanvasOperationApplier operationApplier;

    public InfiniteCanvasEditingUseCase(AnonymousUserResolver anonymousUserResolver,
        InfiniteCanvasRepository infiniteCanvasRepository, InfiniteCanvasEditValidationSupport validationSupport,
        InfiniteCanvasRevisionSupport revisionSupport, InfiniteCanvasEditLockSupport lockSupport,
        InfiniteCanvasOperationApplier operationApplier) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.validationSupport = validationSupport;
        this.revisionSupport = revisionSupport;
        this.lockSupport = lockSupport;
        this.operationApplier = operationApplier;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String roomCode,
        InfiniteCanvasSnapshotRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = validationSupport.normalizeRoomCode(roomCode);
        List<JsonNode> elements = validationSupport.normalizeElements(request == null ? null : request.elements());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            validationSupport.requireParticipant(state, userUuid);
            validationSupport.requireCanvasHost(state, userUuid);
            revisionSupport.requireFreshRevision(request == null ? null : request.baseRevision(), state);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = lockSupport.removeExpiredLocks(state.locks(), now);
            lockSupport.requireNoForeignLocks(locks, userUuid);
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
        String normalizedRoomCode = validationSupport.normalizeRoomCode(roomCode);
        validationSupport.validateCursorRequest(request);

        InfiniteCanvasState state = findActiveState(normalizedRoomCode);
        validationSupport.requireParticipant(state, userUuid);
        InfiniteCanvasCursor cursor = new InfiniteCanvasCursor(userUuid, request.x(), request.y(), request.zoom(),
            request.payload(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return new InfiniteCanvasCursorResponse(normalizedRoomCode, cursor);
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String roomCode,
        InfiniteCanvasOpsRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = validationSupport.normalizeRoomCode(roomCode);
        List<InfiniteCanvasOperationRequest> requestedOperations = validationSupport
            .normalizeOperationRequests(request);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            validationSupport.requireParticipant(state, userUuid);
            List<InfiniteCanvasOperationRequest> pendingOperationRequests = requestedOperations.stream()
                .filter(operationRequest -> !revisionSupport.isAlreadyApplied(state, operationRequest, userUuid))
                .toList();
            if (pendingOperationRequests.isEmpty()) {
                return new InfiniteCanvasOpsAppliedResponse(normalizedRoomCode, state.revision(),
                    state.elements().size(), List.of());
            }
            revisionSupport.requireMergeableRevision(request == null ? null : request.baseRevision(), state,
                pendingOperationRequests);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasOperationApplier.CanvasElementBatch elements = operationApplier
                .createElementBatch(state.elements());
            List<InfiniteCanvasOperation> acceptedOperations = new ArrayList<>();
            Map<String, InfiniteCanvasLock> locks = lockSupport.removeExpiredLocks(state.locks(), now);
            long revision = state.revision();

            for (InfiniteCanvasOperationRequest operationRequest : pendingOperationRequests) {
                lockSupport.validateOperationLock(locks, operationRequest, userUuid);
                revision++;
                InfiniteCanvasOperation operation = operationApplier.createOperation(operationRequest, userUuid,
                    revision, now);
                operationApplier.applyOperation(elements, locks, operation);
                acceptedOperations.add(operation);
            }

            List<JsonNode> updatedElements = elements.toList();
            InfiniteCanvasState updatedState = copyState(state, state.participants(), updatedElements,
                revisionSupport.appendRecentOperations(state.operations(), acceptedOperations), locks, state.cursors(),
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

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        List<JsonNode> elements, List<InfiniteCanvasOperation> operations, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, JsonNode viewport, long revision, LocalDateTime updatedAt,
        LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.roomCode(), state.status(), state.hostUserUuid(), participants, elements,
            operations, locks, cursors, viewport, state.maxParticipants(), revision, state.createdAt(), updatedAt,
            closedAt);
    }
}
