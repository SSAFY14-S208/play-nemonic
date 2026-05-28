package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class FlipbookRoomReconnectPolicySupport {

    private static final String RECONNECT_EXPIRED_MESSAGE = "재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";

    private final FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider;
    private final FlipbookRoomParticipantPolicySupport participantPolicySupport;

    public FlipbookRoomReconnectPolicySupport(FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider,
        FlipbookRoomParticipantPolicySupport participantPolicySupport) {
        this.flipbookRuntimeSettingsProvider = flipbookRuntimeSettingsProvider;
        this.participantPolicySupport = participantPolicySupport;
    }

    public void validateWebSocketConnectableRoom(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.WAITING || roomState.status() == FlipbookRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public boolean requiresReconnectGrace(FlipbookRoomState roomState) {
        return roomState.status() == FlipbookRoomStatus.PLAYING;
    }

    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now) {
        return canReconnect(participant, now, flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now, Duration reconnectGracePeriod) {
        if (participant.dropped()) {
            return false;
        }

        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(reconnectGracePeriod).isBefore(now);
    }

    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now) {
        requireReconnectable(participant, now, flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now,
        Duration reconnectGracePeriod) {
        if (!canReconnect(participant, now, reconnectGracePeriod)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now) {
        validateExistingParticipantReturn(roomState, participant, now,
            flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now, Duration reconnectGracePeriod) {
        if (participant.dropped()) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }

        if (participant.connected()) {
            return;
        }

        if (participant.disconnectedAt() != null && requiresReconnectGrace(roomState)) {
            requireReconnectable(participant, now, reconnectGracePeriod);
        }
    }

    FlipbookRoomViewerBlockedReason findJoinBlockedReason(FlipbookRoomState roomState, String viewerUserUuid) {
        if (participantPolicySupport.isKicked(roomState, viewerUserUuid)) {
            return FlipbookRoomViewerBlockedReason.KICKED;
        }

        if (participantPolicySupport.isDropped(roomState, viewerUserUuid)) {
            return FlipbookRoomViewerBlockedReason.RECONNECT_EXPIRED;
        }

        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return FlipbookRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            return FlipbookRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == FlipbookRoomStatus.FINALIZING || roomState.status() == FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return FlipbookRoomViewerBlockedReason.ROOM_CLOSED;
    }
}
