package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
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
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

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
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

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

    // connected 상태 업데이트 메서드
    private FlipbookRoomStateResponse updateParticipantConnectionState(String viewerUserUuid, String roomCodeValue,
        boolean connected) {
        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            // 현재 방 상태
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);

            // ws 접속할 수 있는 상태인지 (대기방, 플레이 중)
            flipbookRoomPolicy.validateWebSocketConnectableRoom(roomState);

            if (connected) {
                flipbookRoomPolicy.validateNotKicked(roomState, viewerUserUuid);
                flipbookRoomPolicy.validateNotDropped(roomState, viewerUserUuid);
            }

            // WebSocket 연결 대상 참여자를 조회
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireConnectionParticipant(roomState,
                viewerUserUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            if (connected) {
                flipbookRoomPolicy.validateExistingParticipantReturn(roomState, participant, now);
            }

            // 해당 사용자의 connected 상태 업데이트
            FlipbookRoomParticipant updatedParticipant = participant.withConnection(connected, connected ? null : now);
            // 대체
            FlipbookRoomState updatedRoomState = replaceParticipant(roomState, updatedParticipant, now);

            // 저장
            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                flipbookInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                if (connected && participant.disconnectedAt() != null) {
                    FlipbookRoomEventLogger.websocketBusiness("flipbook_ws_reconnected", metadata("room_id",
                        updatedRoomState.roomCode(), "uuid", viewerUserUuid, "room_status", updatedRoomState.status()));
                }
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
