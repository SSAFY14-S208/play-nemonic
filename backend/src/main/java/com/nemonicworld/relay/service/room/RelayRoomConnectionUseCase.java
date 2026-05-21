package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsSnapshot;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 WebSocket 연결 상태 변경 유스케이스입니다.
 */
@Service
public class RelayRoomConnectionUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;

    public RelayRoomConnectionUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomViewerFactory relayRoomViewerFactory, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRuntimeSettingsProvider relayRuntimeSettingsProvider) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
    }

    /**
     * WebSocket 연결 성공을 처리합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        return connectRoom(userUuidValue, roomCodeValue, null);
    }

    @Transactional(readOnly = true)
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue, String sessionId) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUser.getId().toString(), roomCodeValue, true, sessionId);
    }

    /**
     * WebSocket 연결 해제를 처리합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        String viewerUserUuid = anonymousUserResolver.parseUuid(userUuidValue).toString();
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUserUuid, roomCodeValue, false, null);
    }

    /**
     * 참여자 연결 상태를 변경합니다.
     */
    private RelayRoomStateResponse updateParticipantConnectionState(String viewerUserUuid, String roomCodeValue,
        boolean connected, String sessionId) {
        RelayRuntimeSettingsSnapshot settings = relayRuntimeSettingsProvider.currentSettingsSnapshot();
        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            if (connected) {
                relayRoomPolicy.validateWebSocketConnectableRoom(roomState);
            }
            RelayRoomParticipant participant = relayRoomPolicy.requireConnectionParticipant(roomState, viewerUserUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            if (connected) {
                relayRoomPolicy.validateNotKicked(roomState, viewerUserUuid);
                relayRoomPolicy.validateNotDropped(roomState, viewerUserUuid);
                if (!participant.connected() && participant.disconnectedAt() != null
                    && relayRoomPolicy.requiresReconnectGrace(roomState)) {
                    relayRoomPolicy.requireReconnectable(participant, now, settings.reconnectGracePeriod());
                }
            }

            RelayRoomParticipant updatedParticipant = participant.withConnection(connected, connected ? null : now);
            RelayRoomState updatedRoomState = replaceParticipant(roomState, updatedParticipant, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, updatedRoomState, now,
                    settings.reconnectGracePeriod());
                if (connected && !participant.connected() && participant.disconnectedAt() != null) {
                    RelayRoomEventLogger.websocketBusiness("relay_ws_reconnected",
                        metadata("room_id", updatedRoomState.roomCode(), "uuid", viewerUserUuid, "old_disconnected_at",
                            participant.disconnectedAt(), "session_id", sessionId, "room_status",
                            updatedRoomState.status(), "current_part", updatedRoomState.currentPart()));
                }

                return RelayRoomStateResponse.from(updatedRoomState, viewer, settings.roomTimeLimitSettings(),
                    settings.reconnectGracePeriod());
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 특정 참여자를 교체한 방 상태를 생성합니다.
     */
    private RelayRoomState replaceParticipant(RelayRoomState roomState, RelayRoomParticipant updatedParticipant,
        LocalDateTime updatedAt) {
        List<RelayRoomParticipant> participants = roomState.participants().stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();

        return roomState.withParticipants(participants, updatedAt);
    }
}
