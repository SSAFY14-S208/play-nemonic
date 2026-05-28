package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayAbandonedRoomCloseUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayAbandonedRoomCloseUseCase.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomCloseEventSupport relayRoomCloseEventSupport;
    private final Duration waitingIdleDuration;
    private final Duration playingAbandonedDuration;
    private final int scanLimit;

    public RelayAbandonedRoomCloseUseCase(RelayRoomRepository relayRoomRepository,
        RelayRoomCloseCommand relayRoomCloseCommand, RelayRoomCloseEventSupport relayRoomCloseEventSupport,
        @Value("${nemonic.relay.abandoned-close.waiting-idle-seconds:300}") long waitingIdleSeconds,
        @Value("${nemonic.relay.abandoned-close.playing-abandoned-seconds:300}") long playingAbandonedSeconds,
        @Value("${nemonic.relay.abandoned-close.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomCloseEventSupport = relayRoomCloseEventSupport;
        this.waitingIdleDuration = Duration.ofSeconds(Math.max(0L, waitingIdleSeconds));
        this.playingAbandonedDuration = Duration.ofSeconds(Math.max(0L, playingAbandonedSeconds));
        this.scanLimit = scanLimit;
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms() {
        return closeAbandonedRooms(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomState> emptyWaitingRooms = relayRoomRepository.findEmptyWaitingRooms(scanLimit);
        List<RelayRoomState> waitingRooms = relayRoomRepository
            .findAbandonedWaitingRooms(closedAt.minus(waitingIdleDuration), scanLimit);
        List<RelayRoomState> playingRooms = relayRoomRepository
            .findAbandonedPlayingRooms(closedAt.minus(playingAbandonedDuration), scanLimit);

        int closedEmptyWaitingRoomCount = closeRooms(emptyWaitingRooms, closedAt, "waiting_empty", null, null);
        int closedWaitingRoomCount = closeRooms(waitingRooms, closedAt, "waiting_idle_timeout", "idle_seconds",
            waitingIdleDuration.getSeconds());
        int closedPlayingRoomCount = closeRooms(playingRooms, closedAt, "playing_abandoned", "abandoned_seconds",
            playingAbandonedDuration.getSeconds());

        return new RelayRoomAbandonedCloseProcessResult(emptyWaitingRooms.size() + waitingRooms.size(),
            closedEmptyWaitingRoomCount + closedWaitingRoomCount, playingRooms.size(), closedPlayingRoomCount);
    }

    private int closeRooms(List<RelayRoomState> rooms, LocalDateTime closedAt, String closeReason,
        String durationFieldName, Long durationSeconds) {
        int closedRoomCount = 0;
        for (RelayRoomState roomState : rooms) {
            try {
                RelayRoomCloseResult result = relayRoomCloseCommand.closeActiveRoomIfUnchanged(roomState, closedAt);
                if (result.closed()) {
                    closedRoomCount++;
                    relayRoomCloseEventSupport.publishAbandonedClosed(roomState, result, closeReason, durationFieldName,
                        durationSeconds);
                }
            } catch (RuntimeException e) {
                log.warn("Failed to close abandoned relay room. roomCode={}, closeReason={}", roomState.roomCode(),
                    closeReason, e);
            }
        }

        return closedRoomCount;
    }
}
