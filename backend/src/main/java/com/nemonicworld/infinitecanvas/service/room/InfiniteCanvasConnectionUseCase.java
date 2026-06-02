package com.nemonicworld.infinitecanvas.service.room;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasConnectionUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final int UPDATE_MAX_RETRIES = 8;

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;

    public InfiniteCanvasConnectionUseCase(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String roomCode) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            InfiniteCanvasParticipant participant = state.findParticipant(userUuid)
                .orElseThrow(() -> new NotFoundException(NOT_PARTICIPANT_MESSAGE));

            if (participant.connected()) {
                return InfiniteCanvasStateResponse.from(state, userUuid);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasParticipant connectedParticipant = participant.connect(now);
            InfiniteCanvasState updatedState = copyState(state,
                replaceParticipant(state.participants(), connectedParticipant), now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String roomCode) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            InfiniteCanvasParticipant participant = state.findParticipant(userUuid)
                .orElseThrow(() -> new NotFoundException(NOT_PARTICIPANT_MESSAGE));
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasParticipant disconnectedParticipant = participant.disconnect(now);
            Map<String, InfiniteCanvasLock> locks = removeParticipantLocks(state.locks(), userUuid, now);
            Map<String, InfiniteCanvasCursor> cursors = new LinkedHashMap<>(state.cursors());
            cursors.remove(userUuid);
            InfiniteCanvasState updatedState = copyState(state, state.hostUserUuid(), state.status(),
                replaceParticipant(state.participants(), disconnectedParticipant), locks, cursors, now,
                state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
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

    private InfiniteCanvasState findActiveState(String roomCode) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (state.status() == InfiniteCanvasStatus.CLOSED) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        LocalDateTime updatedAt) {
        return new InfiniteCanvasState(state.roomCode(), state.status(), state.hostUserUuid(), participants,
            state.elements(), state.operations(), state.locks(), state.cursors(), state.viewport(),
            state.maxParticipants(), state.revision(), state.createdAt(), updatedAt, state.closedAt());
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, String hostUserUuid, InfiniteCanvasStatus status,
        List<InfiniteCanvasParticipant> participants, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, LocalDateTime updatedAt, LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.roomCode(), status, hostUserUuid, participants, state.elements(),
            state.operations(), locks, cursors, state.viewport(), state.maxParticipants(), state.revision(),
            state.createdAt(), updatedAt, closedAt);
    }

    private List<InfiniteCanvasParticipant> replaceParticipant(List<InfiniteCanvasParticipant> participants,
        InfiniteCanvasParticipant updatedParticipant) {
        return participants.stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();
    }

    private Map<String, InfiniteCanvasLock> removeParticipantLocks(Map<String, InfiniteCanvasLock> locks,
        String userUuid, LocalDateTime now) {
        Map<String, InfiniteCanvasLock> activeLocks = new LinkedHashMap<>();
        locks.forEach((elementId, lock) -> {
            if (lock != null && !lock.isExpired(now) && !userUuid.equals(lock.userUuid())) {
                activeLocks.put(elementId, lock);
            }
        });

        return activeLocks;
    }
}
