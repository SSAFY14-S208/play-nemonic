package com.nemonicworld.relay.service.room;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 방 상태 조회 유스케이스입니다.
 */
@Service
public class RelayRoomQueryUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;

    public RelayRoomQueryUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomPolicy relayRoomPolicy,
        RelayRoomViewerFactory relayRoomViewerFactory) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
    }

    /**
     * 릴레이 방 상태를 조회합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
        RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUser.getId().toString(), roomState,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return RelayRoomStateResponse.from(roomState, viewer);
    }
}
