package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCloseResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 방장이 결과 확인이 끝난 플립북 방을 즉시 종료합니다.
 */
@Service
public class FlipbookRoomManualCloseUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookRoomCloseCommand flipbookRoomCloseCommand;

    public FlipbookRoomManualCloseUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookRoomPolicy flipbookRoomPolicy, FlipbookRoomCloseCommand flipbookRoomCloseCommand) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookRoomCloseCommand = flipbookRoomCloseCommand;
    }

    /**
     * FINISHED 방을 delay 없이 CLOSED로 전환합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomCloseResponse closeRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            flipbookRoomPolicy.validateRoomCloseHost(viewerUserUuid, roomState, participant);

            if (roomState.status() == FlipbookRoomStatus.CLOSED) {
                return FlipbookRoomCloseResponse.alreadyClosed(roomState);
            }

            flipbookRoomPolicy.validateManualClosableRoom(roomState);
            FlipbookRoomCloseResult closeResult = flipbookRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState,
                closedAt);
            if (closeResult.closed()) {
                FlipbookRoomEventLogger.apiBusiness("flipbook_room_closed",
                    metadata("room_id", closeResult.roomCode(), "close_reason", "host_manual", "room_status_before",
                        roomState.status(), "participant_count", roomState.participantCount()));
                return FlipbookRoomCloseResponse.closed(closeResult);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
