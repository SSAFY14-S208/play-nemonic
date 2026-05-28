package com.nemonicworld.flipbook.service.close;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class FlipbookRoomCloseService {

    private final FlipbookFinishedRoomCloseUseCase flipbookFinishedRoomCloseUseCase;

    public FlipbookRoomCloseService(FlipbookFinishedRoomCloseUseCase flipbookFinishedRoomCloseUseCase) {
        this.flipbookFinishedRoomCloseUseCase = flipbookFinishedRoomCloseUseCase;
    }

    public FlipbookRoomCloseProcessResult closeFinishedRooms() {
        return flipbookFinishedRoomCloseUseCase.closeFinishedRooms();
    }

    public FlipbookRoomCloseProcessResult closeFinishedRooms(LocalDateTime now) {
        return flipbookFinishedRoomCloseUseCase.closeFinishedRooms(now);
    }

    public FlipbookRoomCloseResult closeRoom(String roomCode, LocalDateTime now) {
        return flipbookFinishedRoomCloseUseCase.closeRoom(roomCode, now);
    }

    public FlipbookRoomCloseResult closeFinishedRoomIfUnchanged(FlipbookRoomState roomState, LocalDateTime now) {
        return flipbookFinishedRoomCloseUseCase.closeFinishedRoomIfUnchanged(roomState, now);
    }
}
