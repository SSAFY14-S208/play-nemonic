package com.nemonicworld.infinitecanvas.service.canvas;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasRevisionConflictResponse;
import com.nemonicworld.infinitecanvas.exception.InfiniteCanvasRevisionConflictException;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasRevisionSupport {

    private static final String BASE_REVISION_REQUIRED_MESSAGE = "baseRevision을 지정해주세요.";
    private static final String STALE_REVISION_MESSAGE = "캔버스 revision이 최신이 아닙니다. 서버 상태를 다시 동기화해주세요.";
    private static final int RECENT_OPERATION_LIMIT = 200;

    private final InfiniteCanvasOperationApplier operationApplier;

    public InfiniteCanvasRevisionSupport(InfiniteCanvasOperationApplier operationApplier) {
        this.operationApplier = operationApplier;
    }

    public void requireFreshRevision(Long baseRevision, InfiniteCanvasState state) {
        if (baseRevision == null) {
            throw new BadRequestException(BASE_REVISION_REQUIRED_MESSAGE);
        }
        if (baseRevision.longValue() != state.revision()) {
            throw new InfiniteCanvasRevisionConflictException(STALE_REVISION_MESSAGE,
                revisionConflictResponse(baseRevision, state));
        }
    }

    public void requireMergeableRevision(Long baseRevision, InfiniteCanvasState state,
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

    public boolean isAlreadyApplied(InfiniteCanvasState state, InfiniteCanvasOperationRequest operationRequest,
        String userUuid) {
        if (operationRequest == null || !StringUtils.hasText(operationRequest.clientOperationId())) {
            return false;
        }

        String clientOperationId = operationRequest.clientOperationId().trim();
        return state.operations().stream().anyMatch(operation -> clientOperationId.equals(operation.clientOperationId())
            && userUuid.equals(operation.userUuid()));
    }

    public List<InfiniteCanvasOperation> appendRecentOperations(List<InfiniteCanvasOperation> currentOperations,
        List<InfiniteCanvasOperation> acceptedOperations) {
        List<InfiniteCanvasOperation> operations = new ArrayList<>(currentOperations);
        operations.addAll(acceptedOperations);
        if (operations.size() <= RECENT_OPERATION_LIMIT) {
            return operations;
        }

        return operations.subList(operations.size() - RECENT_OPERATION_LIMIT, operations.size());
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
            String elementId = operationApplier.resolveOperationElementId(operationRequest);
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
}
