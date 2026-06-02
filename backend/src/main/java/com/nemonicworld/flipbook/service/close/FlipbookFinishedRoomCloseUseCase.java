package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

@Component
public class FlipbookFinishedRoomCloseUseCase {

    private static final Logger log = LoggerFactory.getLogger(FlipbookFinishedRoomCloseUseCase.class);

    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomCloseCommand flipbookRoomCloseCommand;
    private final FlipbookRoomCloseEventSupport flipbookRoomCloseEventSupport;
    private final Duration closeDelay;
    private final int scanLimit;

    public FlipbookFinishedRoomCloseUseCase(FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomCloseCommand flipbookRoomCloseCommand, FlipbookRoomCloseEventSupport flipbookRoomCloseEventSupport,
        @Value("${nemonic.flipbook.close.delay-seconds:300}") long closeDelaySeconds,
        @Value("${nemonic.flipbook.close.scan-limit:100}") int scanLimit) {
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomCloseCommand = flipbookRoomCloseCommand;
        this.flipbookRoomCloseEventSupport = flipbookRoomCloseEventSupport;
        this.closeDelay = Duration.ofSeconds(Math.max(0L, closeDelaySeconds));
        this.scanLimit = scanLimit;
    }

    public FlipbookRoomCloseProcessResult closeFinishedRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return closeFinishedRooms(now);
    }

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
                log.warn("?뚮┰遺?諛?close 泥섎━ 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎. roomCode={}", closableRoom.roomCode(), e);
                FlipbookRoomEventLogger.apiWarn("flipbook_room_close_failed", "failed to close finished flipbook room",
                    metadata("room_id", closableRoom.roomCode()), e);
            }
        }

        return new FlipbookRoomCloseProcessResult(closableRooms.size(), closedRoomCount);
    }

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

    public FlipbookRoomCloseResult closeFinishedRoomIfUnchanged(FlipbookRoomState roomState, LocalDateTime now) {
        LocalDateTime closedAt = now.truncatedTo(ChronoUnit.SECONDS);
        if (roomState == null || roomState.status() != FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomCloseResult.noOp(roomState == null ? null : roomState.roomCode());
        }

        FlipbookRoomCloseResult closeResult = flipbookRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState,
            closedAt);
        if (closeResult.closed()) {
            flipbookRoomCloseEventSupport.publishAutoDelayClosed(roomState, closeResult);
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
