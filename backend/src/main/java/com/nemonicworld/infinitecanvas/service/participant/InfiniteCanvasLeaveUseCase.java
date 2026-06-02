package com.nemonicworld.infinitecanvas.service.participant;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
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
public class InfiniteCanvasLeaveUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final int UPDATE_MAX_RETRIES = 8;

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;

    public InfiniteCanvasLeaveUseCase(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String roomCode) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedRoomCode);
            if (!state.hasParticipant(userUuid)) {
                throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            List<InfiniteCanvasParticipant> participants = state.participants().stream()
                .filter(participant -> !participant.userUuid().equals(userUuid)).toList();
            Map<String, InfiniteCanvasLock> locks = removeParticipantLocks(state.locks(), userUuid, now);
            Map<String, InfiniteCanvasCursor> cursors = new LinkedHashMap<>(state.cursors());
            cursors.remove(userUuid);

            if (participants.isEmpty()) {
                InfiniteCanvasState closedState = copyState(state, state.hostUserUuid(), InfiniteCanvasStatus.CLOSED,
                    participants, locks, cursors, now, now);
                if (infiniteCanvasRepository.saveIfUnchanged(state, closedState)) {
                    infiniteCanvasRepository.delete(normalizedRoomCode);
                    return new InfiniteCanvasLeaveResponse(normalizedRoomCode, userUuid,
                        findLeavingNickname(state, userUuid), 0, false, null, null, true, now);
                }
                continue;
            }

            HostTransferResult hostTransfer = transferHostIfNeeded(state, participants);
            InfiniteCanvasState updatedState = copyState(state, hostTransfer.hostUserUuid(), state.status(),
                hostTransfer.participants(), locks, cursors, now, state.closedAt());
            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return new InfiniteCanvasLeaveResponse(normalizedRoomCode, userUuid,
                    findLeavingNickname(state, userUuid), updatedState.participantCount(), hostTransfer.hostChanged(),
                    hostTransfer.newHostUserUuid(), hostTransfer.newHostNickname(), false, null);
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
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
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

    private String findLeavingNickname(InfiniteCanvasState state, String userUuid) {
        return state.findParticipant(userUuid).map(InfiniteCanvasParticipant::nickname).orElse(null);
    }

    private HostTransferResult transferHostIfNeeded(InfiniteCanvasState state,
        List<InfiniteCanvasParticipant> participants) {
        if (participants.stream().anyMatch(participant -> participant.userUuid().equals(state.hostUserUuid()))) {
            return new HostTransferResult(state.hostUserUuid(), participants, false, null, null);
        }

        InfiniteCanvasParticipant newHost = participants.getFirst();
        List<InfiniteCanvasParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHost.userUuid()))).toList();

        return new HostTransferResult(newHost.userUuid(), transferredParticipants, true, newHost.userUuid(),
            newHost.nickname());
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, String hostUserUuid, InfiniteCanvasStatus status,
        List<InfiniteCanvasParticipant> participants, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, LocalDateTime updatedAt, LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.roomCode(), status, hostUserUuid, participants, state.elements(),
            state.operations(), locks, cursors, state.viewport(), state.maxParticipants(), state.revision(),
            state.createdAt(), updatedAt, closedAt);
    }

    private record HostTransferResult(String hostUserUuid, List<InfiniteCanvasParticipant> participants,
        boolean hostChanged, String newHostUserUuid, String newHostNickname) {
    }
}
