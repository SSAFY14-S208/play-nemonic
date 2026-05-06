package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 결과 확인 시간이 지난 FINISHED 릴레이 방을 CLOSED로 전환합니다.
 */
@Service
public class RelayRoomCloseService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomCloseService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomCloseCommand relayRoomCloseCommand;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final Duration closeDelay;
    private final int scanLimit;

    public RelayRoomCloseService(RelayRoomRepository relayRoomRepository, RelayRoomCloseCommand relayRoomCloseCommand,
        RelayRoomEventPublisher relayRoomEventPublisher,
        @Value("${nemonic.relay.close.delay-seconds:300}") long closeDelaySeconds,
        @Value("${nemonic.relay.close.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.closeDelay = Duration.ofSeconds(Math.max(0L, closeDelaySeconds));
        this.scanLimit = scanLimit;
    }

    /**
     * 설정된 close delay가 지난 FINISHED 방을 찾아 CLOSED로 전환합니다.
     */
    public RelayRoomCloseProcessResult closeFinishedRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return closeFinishedRooms(now);
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 기준 시각을 받아 close 스캔을 실행합니다.
     */
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
                log.warn("릴레이 방 close 처리 중 오류가 발생했습니다. roomCode={}", closableRoom.roomCode(), e);
            }
        }

        return new RelayRoomCloseProcessResult(closableRooms.size(), closedRoomCount);
    }

    /**
     * 방 하나를 최신 Redis 상태 기준으로 다시 확인한 뒤 CLOSED로 전환합니다.
     */
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

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 이미 검증한 FINISHED 방을 delay 없이 CLOSED로 전환합니다.
     */
    public RelayRoomCloseResult closeFinishedRoomIfUnchanged(RelayRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINISHED) {
            return RelayRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState, closedAt);
        if (closeResult.closed()) {
            relayRoomEventPublisher.publishRoomClosed(roomState.roomCode(), closedAt);
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
