package com.nemonicworld.infinitecanvas.service.lock;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasLockUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String INVALID_LOCK_MESSAGE = "요소 lock 요청 형식이 올바르지 않습니다.";
    private static final String LOCK_CONFLICT_MESSAGE = "다른 참여자가 해당 요소를 편집 중입니다.";
    private static final String LOCK_OWNER_MESSAGE = "요소 lock을 보유한 참여자만 해제할 수 있습니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int UPDATE_MAX_RETRIES = 8;

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;

    public InfiniteCanvasLockUseCase(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        InfiniteCanvasRepository infiniteCanvasRepository) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse acquireLock(String userUuidValue, String roomCode,
        InfiniteCanvasLockRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        String elementId = normalizeElementId(request == null ? null : request.elementId());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            requireParticipant(state, userUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            InfiniteCanvasLock existingLock = locks.get(elementId);
            if (existingLock != null && !userUuid.equals(existingLock.userUuid())) {
                throw new ConflictException(LOCK_CONFLICT_MESSAGE);
            }

            InfiniteCanvasLock lock = new InfiniteCanvasLock(elementId, userUuid, now, now.plus(LOCK_TTL));
            locks.put(elementId, lock);
            InfiniteCanvasState updatedState = copyStateWithLocks(state, locks, now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasLockResponse(normalizedRoomCode, elementId, lock);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse releaseLock(String userUuidValue, String roomCode,
        InfiniteCanvasLockRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        String elementId = normalizeElementId(request == null ? null : request.elementId());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            requireParticipant(state, userUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            InfiniteCanvasLock existingLock = locks.get(elementId);
            if (existingLock != null && !userUuid.equals(existingLock.userUuid())) {
                throw new ForbiddenException(LOCK_OWNER_MESSAGE);
            }

            locks.remove(elementId);
            InfiniteCanvasState updatedState = copyStateWithLocks(state, locks, now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasLockResponse(normalizedRoomCode, elementId, null);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    private String normalizeElementId(String elementId) {
        if (!StringUtils.hasText(elementId) || elementId.trim().length() > 128) {
            throw new BadRequestException(INVALID_LOCK_MESSAGE);
        }

        return elementId.trim();
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

    private InfiniteCanvasState copyStateWithLocks(InfiniteCanvasState state, Map<String, InfiniteCanvasLock> locks,
        LocalDateTime updatedAt) {
        return new InfiniteCanvasState(state.roomCode(), state.status(), state.hostUserUuid(), state.participants(),
            state.elements(), state.operations(), locks, state.cursors(), state.viewport(), state.maxParticipants(),
            state.revision(), state.createdAt(), updatedAt, state.closedAt());
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
