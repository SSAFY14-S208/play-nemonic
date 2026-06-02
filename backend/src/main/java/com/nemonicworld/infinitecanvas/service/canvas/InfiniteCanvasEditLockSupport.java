package com.nemonicworld.infinitecanvas.service.canvas;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasEditLockSupport {

    private static final String INVALID_OPERATIONS_MESSAGE = "캔버스 편집 연산 목록 형식이 올바르지 않습니다.";
    private static final String LOCK_CONFLICT_MESSAGE = "다른 참여자가 해당 요소를 편집 중입니다.";

    private final InfiniteCanvasOperationApplier operationApplier;

    public InfiniteCanvasEditLockSupport(InfiniteCanvasOperationApplier operationApplier) {
        this.operationApplier = operationApplier;
    }

    public Map<String, InfiniteCanvasLock> removeExpiredLocks(Map<String, InfiniteCanvasLock> locks,
        LocalDateTime now) {
        Map<String, InfiniteCanvasLock> activeLocks = new LinkedHashMap<>();
        locks.forEach((elementId, lock) -> {
            if (lock != null && !lock.isExpired(now)) {
                activeLocks.put(elementId, lock);
            }
        });

        return activeLocks;
    }

    public void validateOperationLock(Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperationRequest operationRequest, String userUuid) {
        InfiniteCanvasOperationType operationType = operationRequest.operationType();
        if (operationType == InfiniteCanvasOperationType.CLEAR_CANVAS) {
            requireNoForeignLocks(locks, userUuid);
            return;
        }

        String elementId = operationApplier.resolveOperationElementId(operationRequest);
        if (operationType == InfiniteCanvasOperationType.DELETE_ELEMENT && !StringUtils.hasText(elementId)) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        requireElementEditable(locks, elementId, userUuid);
    }

    public void requireNoForeignLocks(Map<String, InfiniteCanvasLock> locks, String userUuid) {
        boolean hasForeignLock = locks.values().stream()
            .anyMatch(lock -> lock != null && !userUuid.equals(lock.userUuid()));
        if (hasForeignLock) {
            throw new ConflictException(LOCK_CONFLICT_MESSAGE);
        }
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
}
