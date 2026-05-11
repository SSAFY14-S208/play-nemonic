package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Service
public class RelayRoomAbandonedCloseService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomAbandonedCloseService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final Duration waitingIdleDuration;
    private final Duration playingAbandonedDuration;
    private final int scanLimit;

    public RelayRoomAbandonedCloseService(RelayRoomRepository relayRoomRepository,
        RelayRoomCloseCommand relayRoomCloseCommand, RelayRoomEventPublisher relayRoomEventPublisher,
        @Value("${nemonic.relay.abandoned-close.waiting-idle-seconds:300}") long waitingIdleSeconds,
        @Value("${nemonic.relay.abandoned-close.playing-abandoned-seconds:300}") long playingAbandonedSeconds,
        @Value("${nemonic.relay.abandoned-close.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.waitingIdleDuration = Duration.ofSeconds(Math.max(0L, waitingIdleSeconds));
        this.playingAbandonedDuration = Duration.ofSeconds(Math.max(0L, playingAbandonedSeconds));
        this.scanLimit = scanLimit;
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms() {
        return closeAbandonedRooms(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomState> waitingRooms = relayRoomRepository
            .findAbandonedWaitingRooms(closedAt.minus(waitingIdleDuration), scanLimit);
        List<RelayRoomState> playingRooms = relayRoomRepository
            .findAbandonedPlayingRooms(closedAt.minus(playingAbandonedDuration), scanLimit);

        int closedWaitingRoomCount = closeRooms(waitingRooms, closedAt, "waiting_idle_timeout", "idle_seconds",
            waitingIdleDuration.getSeconds());
        int closedPlayingRoomCount = closeRooms(playingRooms, closedAt, "playing_abandoned", "abandoned_seconds",
            playingAbandonedDuration.getSeconds());

        return new RelayRoomAbandonedCloseProcessResult(waitingRooms.size(), closedWaitingRoomCount,
            playingRooms.size(), closedPlayingRoomCount);
    }

    private int closeRooms(List<RelayRoomState> rooms, LocalDateTime closedAt, String closeReason,
        String durationFieldName, long durationSeconds) {
        int closedRoomCount = 0;
        for (RelayRoomState roomState : rooms) {
            try {
                RelayRoomCloseResult result = relayRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
                if (result.closed()) {
                    closedRoomCount++;
                    logRoomClosed(roomState, result, closeReason, durationFieldName, durationSeconds);
                    relayRoomEventPublisher.publishRoomClosed(roomState.roomCode(), result.closedAt());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to close abandoned relay room. roomCode={}, closeReason={}", roomState.roomCode(),
                    closeReason, e);
            }
        }

        return closedRoomCount;
    }

    private void logRoomClosed(RelayRoomState previousRoomState, RelayRoomCloseResult closeResult, String closeReason,
        String durationFieldName, long durationSeconds) {
        RelayRoomEventLogger.apiBusiness("relay_room_closed",
            metadata("room_id", closeResult.roomCode(), "close_reason", closeReason, "room_status_before",
                previousRoomState.status(), "participant_count", previousRoomState.participantCount(),
                durationFieldName, durationSeconds, "closed_at", closeResult.closedAt()));
    }
}
