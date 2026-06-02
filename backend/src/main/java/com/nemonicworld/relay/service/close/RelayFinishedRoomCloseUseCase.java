package com.nemonicworld.relay.service.close;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayFinishedRoomCloseUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayFinishedRoomCloseUseCase.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomCloseEventSupport relayRoomCloseEventSupport;
    private final Duration closeDelay;
    private final int scanLimit;

    public RelayFinishedRoomCloseUseCase(RelayRoomRepository relayRoomRepository,
        RelayRoomCloseCommand relayRoomCloseCommand, RelayRoomCloseEventSupport relayRoomCloseEventSupport,
        @Value("${nemonic.relay.close.delay-seconds:300}") long closeDelaySeconds,
        @Value("${nemonic.relay.close.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomCloseEventSupport = relayRoomCloseEventSupport;
        this.closeDelay = Duration.ofSeconds(Math.max(0L, closeDelaySeconds));
        this.scanLimit = scanLimit;
    }

    public RelayRoomCloseProcessResult closeFinishedRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return closeFinishedRooms(now);
    }

    public RelayRoomCloseProcessResult closeFinishedRooms(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime closeCutoff = closedAt.minus(closeDelay);
        List<RelayRoomState> closableRooms = relayRoomRepository.findClosableFinishedRooms(closeCutoff, scanLimit);
        int closedRoomCount = 0;

        for (RelayRoomState closableRoom : closableRooms) {
            try {
                RelayRoomCloseResult result = closeRoom(closableRoom.roomCode(), closedAt);
                if (result.closed()) {
                    closedRoomCount++;
                }
            } catch (RuntimeException e) {
                log.warn("由대젅??諛?close 泥섎━ 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎. roomCode={}", closableRoom.roomCode(), e);
            }
        }

        return new RelayRoomCloseProcessResult(closableRooms.size(), closedRoomCount);
    }

    public RelayRoomCloseResult closeRoom(String roomCode, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (!isClosableFinishedRoom(roomState, closedAt)) {
                return RelayRoomCloseResult.noOp(roomCode);
            }

            RelayRoomCloseResult closeResult = closeFinishedRoomIfUnchanged(roomState, closedAt);
            if (closeResult.closed()) {
                return closeResult;
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    public RelayRoomCloseResult closeFinishedRoomIfUnchanged(RelayRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINISHED) {
            return RelayRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState, closedAt);
        if (closeResult.closed()) {
            relayRoomCloseEventSupport.publishAutoDelayClosed(roomState, closeResult);
        }

        return closeResult;
    }

    private boolean isClosableFinishedRoom(RelayRoomState roomState, LocalDateTime now) {
        if (roomState == null || roomState.status() != RelayRoomStatus.FINISHED || roomState.updatedAt() == null) {
            return false;
        }

        return !roomState.updatedAt().isAfter(now.minus(closeDelay));
    }
}
