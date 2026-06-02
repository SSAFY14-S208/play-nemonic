package com.nemonicworld.relay.service.close;

import com.nemonicworld.relay.redis.RelayRoomState;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class RelayRoomCloseService {

    private final RelayFinishedRoomCloseUseCase relayFinishedRoomCloseUseCase;

    public RelayRoomCloseService(RelayFinishedRoomCloseUseCase relayFinishedRoomCloseUseCase) {
        this.relayFinishedRoomCloseUseCase = relayFinishedRoomCloseUseCase;
    }

    public RelayRoomCloseProcessResult closeFinishedRooms() {
        return relayFinishedRoomCloseUseCase.closeFinishedRooms();
    }

    public RelayRoomCloseProcessResult closeFinishedRooms(LocalDateTime now) {
        return relayFinishedRoomCloseUseCase.closeFinishedRooms(now);
    }

    public RelayRoomCloseResult closeRoom(String roomCode, LocalDateTime now) {
        return relayFinishedRoomCloseUseCase.closeRoom(roomCode, now);
    }

    public RelayRoomCloseResult closeFinishedRoomIfUnchanged(RelayRoomState roomState, LocalDateTime now) {
        return relayFinishedRoomCloseUseCase.closeFinishedRoomIfUnchanged(roomState, now);
    }
}
