package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
