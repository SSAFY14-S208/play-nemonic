package com.nemonicworld.relay.service.support;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomReconnectPolicySupport {

    private static final String ROOM_FULL_MESSAGE = "방 정원이 가득 찼습니다.";
    private static final String GAME_IN_PROGRESS_MESSAGE = "게임이 진행 중입니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;
    private final RelayRoomParticipantPolicySupport participantPolicySupport;

    public RelayRoomReconnectPolicySupport(RelayRuntimeSettingsProvider relayRuntimeSettingsProvider,
        RelayRoomParticipantPolicySupport participantPolicySupport) {
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
        this.participantPolicySupport = participantPolicySupport;
    }

    public boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        return canReconnect(participant, now, relayRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now, Duration reconnectGracePeriod) {
        if (participant.dropped()) {
            return false;
        }

        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(reconnectGracePeriod).isBefore(now);
    }

    public boolean requiresReconnectGrace(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.PLAYING;
    }

    public boolean allowsReconnectWithoutGrace(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.WAITING;
    }

    public void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now) {
        requireReconnectable(participant, now, relayRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now,
        Duration reconnectGracePeriod) {
        if (!canReconnect(participant, now, reconnectGracePeriod)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    public void validateWebSocketConnectableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING || roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public void validateJoinableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                throw new ConflictException(ROOM_FULL_MESSAGE);
            }

            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_IN_PROGRESS_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState, String viewerUserUuid) {
        if (participantPolicySupport.isKicked(roomState, viewerUserUuid)) {
            return RelayRoomViewerBlockedReason.KICKED;
        }

        if (roomState.status() == RelayRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return RelayRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            return RelayRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == RelayRoomStatus.FINISHED) {
            return RelayRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return RelayRoomViewerBlockedReason.ROOM_CLOSED;
    }
}
