package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import org.springframework.stereotype.Component;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

@Component
public class FlipbookRoomCloseEventSupport {

    private static final String AUTO_DELAY_REASON = "auto_delay";

    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    public FlipbookRoomCloseEventSupport(FlipbookRoomEventPublisher flipbookRoomEventPublisher) {
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
    }

    public void publishAutoDelayClosed(FlipbookRoomState previousRoomState, FlipbookRoomCloseResult closeResult) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", AUTO_DELAY_REASON, "room_status_before",
                previousRoomState.status(), "participant_count", previousRoomState.participantCount()));
        flipbookRoomEventPublisher.publishRoomClosed(previousRoomState.roomCode(), closeResult.closedAt(),
            AUTO_DELAY_REASON);
    }

    public void publishAbandonedClosed(FlipbookRoomState previousRoomState, FlipbookRoomCloseResult closeResult,
        String closeReason, String durationFieldName, Long durationSeconds) {
        logAbandonedRoomClosed(previousRoomState, closeResult, closeReason, durationFieldName, durationSeconds);
        flipbookRoomEventPublisher.publishRoomClosed(previousRoomState.roomCode(), closeResult.closedAt(), closeReason);
    }

    private void logAbandonedRoomClosed(FlipbookRoomState previousRoomState, FlipbookRoomCloseResult closeResult,
        String closeReason, String durationFieldName, Long durationSeconds) {
        if (durationFieldName == null) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
                metadata("room_id", closeResult.roomCode(), "close_reason", closeReason, "room_status_before",
                    previousRoomState.status(), "participant_count", previousRoomState.participantCount(), "closed_at",
                    closeResult.closedAt()));
            return;
        }

        FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", closeReason, "room_status_before",
                previousRoomState.status(), "participant_count", previousRoomState.participantCount(),
                durationFieldName, durationSeconds, "closed_at", closeResult.closedAt()));
    }
}
