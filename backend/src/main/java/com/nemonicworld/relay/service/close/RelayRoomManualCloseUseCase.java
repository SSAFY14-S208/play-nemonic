package com.nemonicworld.relay.service.close;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomCloseResponse;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장이 결과 확인이 끝난 릴레이 방을 즉시 종료합니다.
 */
@Service
public class RelayRoomManualCloseUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomCloseCommand relayRoomCloseCommand;

    public RelayRoomManualCloseUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomPolicy relayRoomPolicy,
        RelayRoomCloseCommand relayRoomCloseCommand) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomCloseCommand = relayRoomCloseCommand;
    }

    /**
     * FINISHED 방을 delay 없이 CLOSED로 전환합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomCloseResponse closeRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            relayRoomPolicy.validateRoomCloseHost(viewerUserUuid, roomState, participant);

            if (roomState.status() == RelayRoomStatus.CLOSED) {
                return RelayRoomCloseResponse.alreadyClosed(roomState);
            }

            relayRoomPolicy.validateManualClosableRoom(roomState);
            RelayRoomCloseResult closeResult = relayRoomCloseCommand.closeFinishedRoomIfUnchanged(roomState, closedAt);
            if (closeResult.closed()) {
                return RelayRoomCloseResponse.closed(closeResult);
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
