package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import org.springframework.stereotype.Component;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Component
public class RelayRoomCloseEventSupport {

    private static final String AUTO_DELAY_REASON = "auto_delay";

    private final RelayRoomEventPublisher relayRoomEventPublisher;

    public RelayRoomCloseEventSupport(RelayRoomEventPublisher relayRoomEventPublisher) {
        this.relayRoomEventPublisher = relayRoomEventPublisher;
    }

    public void publishAutoDelayClosed(RelayRoomState previousRoomState, RelayRoomCloseResult closeResult) {
        RelayRoomEventLogger.apiBusiness("relay_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", AUTO_DELAY_REASON, "room_status_before",
                previousRoomState.status(), "participant_count", previousRoomState.participantCount()));
        relayRoomEventPublisher.publishRoomClosed(previousRoomState.roomCode(), closeResult.closedAt(),
            AUTO_DELAY_REASON);
    }

    public void publishAbandonedClosed(RelayRoomState previousRoomState, RelayRoomCloseResult closeResult,
        String closeReason, String durationFieldName, Long durationSeconds) {
        logAbandonedRoomClosed(previousRoomState, closeResult, closeReason, durationFieldName, durationSeconds);
        relayRoomEventPublisher.publishRoomClosed(previousRoomState.roomCode(), closeResult.closedAt(), closeReason);
    }

    private void logAbandonedRoomClosed(RelayRoomState previousRoomState, RelayRoomCloseResult closeResult,
        String closeReason, String durationFieldName, Long durationSeconds) {
        if (durationFieldName == null) {
            RelayRoomEventLogger.apiBusiness("relay_room_closed",
                metadata("room_id", closeResult.roomCode(), "close_reason", closeReason, "room_status_before",
                    previousRoomState.status(), "participant_count", previousRoomState.participantCount(), "closed_at",
                    closeResult.closedAt()));
            return;
        }

        RelayRoomEventLogger.apiBusiness("relay_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", closeReason, "room_status_before",
                previousRoomState.status(), "participant_count", previousRoomState.participantCount(),
                durationFieldName, durationSeconds, "closed_at", closeResult.closedAt()));
    }
}
