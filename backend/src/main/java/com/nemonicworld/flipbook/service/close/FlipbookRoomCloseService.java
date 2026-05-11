package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.FlipbookRoomPolicy;
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

/**
 * 결과 확인 시간이 지난 FINISHED 플립북 방을 CLOSED로 전환합니다.
 */
@Service
public class FlipbookRoomCloseService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomCloseService.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomCloseCommand flipbookRoomCloseCommand;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final Duration closeDelay;
    private final int scanLimit;

    public FlipbookRoomCloseService(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomCloseCommand flipbookRoomCloseCommand, FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        @Value("${nemonic.flipbook.close.delay-seconds:300}") long closeDelaySeconds,
        @Value("${nemonic.flipbook.close.scan-limit:100}") int scanLimit) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomCloseCommand = flipbookRoomCloseCommand;
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.closeDelay = Duration.ofSeconds(Math.max(0L, closeDelaySeconds));
        this.scanLimit = scanLimit;
    }

    /**
     * 설정된 close delay가 지난 FINISHED 방을 찾아 CLOSED로 전환합니다.
     */
    public FlipbookRoomCloseProcessResult closeFinishedRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return closeFinishedRooms(now);
    }

    /**
     * 테스트에서 시간을 고정할 수 있도록 기준 시각을 받아 close 스캔을 실행합니다.
     */
    public FlipbookRoomCloseProcessResult closeFinishedRooms(LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime closeCutoff = closedAt.minus(closeDelay);
        List<FlipbookRoomState> closableRooms = flipbookRoomRepository.findClosableFinishedRooms(closeCutoff,
            scanLimit);
        int closedRoomCount = 0;

        for (FlipbookRoomState closableRoom : closableRooms) {
            try {
                FlipbookRoomCloseResult result = closeRoom(closableRoom.roomCode(), closedAt);
                if (result.closed()) {
                    closedRoomCount++;
                }
            } catch (RuntimeException e) {
                log.warn("플립북 방 close 처리 중 오류가 발생했습니다. roomCode={}", closableRoom.roomCode(), e);
                FlipbookRoomEventLogger.apiWarn("flipbook_room_close_failed", "failed to close finished flipbook room",
                    metadata("room_id", closableRoom.roomCode()), e);
            }
        }

        return new FlipbookRoomCloseProcessResult(closableRooms.size(), closedRoomCount);
    }

    /**
     * 방 하나를 최신 Redis 상태 기준으로 다시 확인한 뒤 CLOSED로 전환합니다.
     */
    public FlipbookRoomCloseResult closeRoom(String roomCode, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (!isClosableFinishedRoom(roomState, closedAt)) {
                return FlipbookRoomCloseResult.noOp(roomCode);
            }

            FlipbookRoomCloseResult closeResult = closeFinishedRoomIfUnchanged(roomState, closedAt);
            if (closeResult.closed()) {
                return closeResult;
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 이미 검증한 FINISHED 방을 delay 없이 CLOSED로 전환합니다.
     */
    public FlipbookRoomCloseResult closeFinishedRoomIfUnchanged(FlipbookRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        FlipbookRoomCloseResult closeResult = flipbookRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState,
            closedAt);
        if (closeResult.closed()) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
                metadata("room_id", closeResult.roomCode(), "close_reason", "auto_delay", "room_status_before",
                    roomState.status(), "participant_count", roomState.participantCount()));
            flipbookRoomEventPublisher.publishRoomClosed(roomState.roomCode(), closedAt);
        }

        return closeResult;
    }

    private boolean isClosableFinishedRoom(FlipbookRoomState roomState, LocalDateTime now) {
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINISHED || roomState.updatedAt() == null) {
            return false;
        }

        return !roomState.updatedAt().isAfter(now.minus(closeDelay));
    }
}
