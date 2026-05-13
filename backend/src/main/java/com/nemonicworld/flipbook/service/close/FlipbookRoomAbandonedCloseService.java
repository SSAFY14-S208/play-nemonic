package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

@Service
public class FlipbookRoomAbandonedCloseService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomAbandonedCloseService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomCloseCommand flipbookRoomCloseCommand;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final Duration waitingIdleDuration;
    private final Duration playingAbandonedDuration;
    private final int scanLimit;

    public FlipbookRoomAbandonedCloseService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomCloseCommand flipbookRoomCloseCommand, FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        @Value("${nemonic.flipbook.abandoned-close.waiting-idle-seconds:300}") long waitingIdleSeconds,
        @Value("${nemonic.flipbook.abandoned-close.playing-abandoned-seconds:300}") long playingAbandonedSeconds,
        @Value("${nemonic.flipbook.abandoned-close.scan-limit:100}") int scanLimit) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomCloseCommand = flipbookRoomCloseCommand;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.waitingIdleDuration = Duration.ofSeconds(Math.max(0L, waitingIdleSeconds));
        this.playingAbandonedDuration = Duration.ofSeconds(Math.max(0L, playingAbandonedSeconds));
        this.scanLimit = scanLimit;
    }

    public FlipbookRoomAbandonedCloseProcessResult closeAbandonedRooms() {
        return closeAbandonedRooms(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public FlipbookRoomAbandonedCloseProcessResult closeAbandonedRooms(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        List<FlipbookRoomState> emptyWaitingRooms = flipbookRoomRepository.findEmptyWaitingRooms(scanLimit);
        List<FlipbookRoomState> waitingRooms = flipbookRoomRepository
            .findAbandonedWaitingRooms(closedAt.minus(waitingIdleDuration), scanLimit);
        List<FlipbookRoomState> playingRooms = flipbookRoomRepository
            .findAbandonedPlayingRooms(closedAt.minus(playingAbandonedDuration), scanLimit);

        int closedEmptyWaitingRoomCount = closeRooms(emptyWaitingRooms, closedAt, "waiting_empty", null, null);
        int closedWaitingRoomCount = closeRooms(waitingRooms, closedAt, "waiting_idle_timeout", "idle_seconds",
            waitingIdleDuration.getSeconds());
        int closedPlayingRoomCount = closeRooms(playingRooms, closedAt, "playing_abandoned", "abandoned_seconds",
            playingAbandonedDuration.getSeconds());

        return new FlipbookRoomAbandonedCloseProcessResult(emptyWaitingRooms.size() + waitingRooms.size(),
            closedEmptyWaitingRoomCount + closedWaitingRoomCount, playingRooms.size(), closedPlayingRoomCount);
    }

    private int closeRooms(List<FlipbookRoomState> rooms, LocalDateTime closedAt, String closeReason,
        String durationFieldName, Long durationSeconds) {
        int closedRoomCount = 0;
        for (FlipbookRoomState roomState : rooms) {
            try {
                FlipbookRoomCloseResult result = flipbookRoomCloseCommand.closeActiveRoomIfUnchanged(roomState,
                    closedAt);
                if (result.closed()) {
                    closedRoomCount++;
                    logRoomClosed(roomState, result, closeReason, durationFieldName, durationSeconds);
                    flipbookRoomEventPublisher.publishRoomClosed(roomState.roomCode(), result.closedAt());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to close abandoned flipbook room. roomCode={}, closeReason={}", roomState.roomCode(),
                    closeReason, e);
            }
        }

        return closedRoomCount;
    }

    private void logRoomClosed(FlipbookRoomState previousRoomState, FlipbookRoomCloseResult closeResult,
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
