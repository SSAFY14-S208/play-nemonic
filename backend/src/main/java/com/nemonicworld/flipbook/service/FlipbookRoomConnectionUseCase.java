package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플립북 WebSocket 연결 상태 변경 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomConnectionUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookRoomViewerFactory flipbookRoomViewerFactory;

    /**
     * WebSocket CONNECT 성공을 Redis 참여자 상태에 반영합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUser.getId().toString(), roomCodeValue, true);
    }

    /**
     * WebSocket DISCONNECT를 Redis 참여자 상태에 반영합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        String viewerUserUuid = anonymousUserResolver.parseUuid(userUuidValue).toString();
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUserUuid, roomCodeValue, false);
    }

    private FlipbookRoomStateResponse updateParticipantConnectionState(String viewerUserUuid, String roomCodeValue,
        boolean connected) {
        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            flipbookRoomPolicy.validateWebSocketConnectableRoom(roomState);
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireConnectionParticipant(roomState,
                viewerUserUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            FlipbookRoomParticipant updatedParticipant = new FlipbookRoomParticipant(participant.userUuid(),
                participant.nickname(), participant.host(), participant.joinOrder(), connected, connected ? null : now,
                participant.joinedAt());
            FlipbookRoomState updatedRoomState = replaceParticipant(roomState, updatedParticipant, now);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                FlipbookRoomViewerResponse viewer = flipbookRoomViewerFactory.create(viewerUserUuid, updatedRoomState);

                return FlipbookRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_CONNECTION_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * record 상태를 직접 수정하지 않고 특정 참여자만 교체한 새 방 상태를 생성합니다.
     */
    private FlipbookRoomState replaceParticipant(FlipbookRoomState roomState,
        FlipbookRoomParticipant updatedParticipant, LocalDateTime updatedAt) {
        List<FlipbookRoomParticipant> participants = roomState.participants().stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();

        return roomState.withParticipants(participants, updatedAt);
    }
}
