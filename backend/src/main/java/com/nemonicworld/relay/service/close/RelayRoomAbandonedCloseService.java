package com.nemonicworld.relay.service.close;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class RelayRoomAbandonedCloseService {

    private final RelayAbandonedRoomCloseUseCase relayAbandonedRoomCloseUseCase;

    public RelayRoomAbandonedCloseService(RelayAbandonedRoomCloseUseCase relayAbandonedRoomCloseUseCase) {
        this.relayAbandonedRoomCloseUseCase = relayAbandonedRoomCloseUseCase;
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms() {
        return relayAbandonedRoomCloseUseCase.closeAbandonedRooms();
    }

    public RelayRoomAbandonedCloseProcessResult closeAbandonedRooms(LocalDateTime now) {
        return relayAbandonedRoomCloseUseCase.closeAbandonedRooms(now);
    }
}
