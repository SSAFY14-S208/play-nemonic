package com.nemonicworld.flipbook.service.close;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class FlipbookRoomAbandonedCloseService {

    private final FlipbookAbandonedRoomCloseUseCase flipbookAbandonedRoomCloseUseCase;

    public FlipbookRoomAbandonedCloseService(FlipbookAbandonedRoomCloseUseCase flipbookAbandonedRoomCloseUseCase) {
        this.flipbookAbandonedRoomCloseUseCase = flipbookAbandonedRoomCloseUseCase;
    }

    public FlipbookRoomAbandonedCloseProcessResult closeAbandonedRooms() {
        return flipbookAbandonedRoomCloseUseCase.closeAbandonedRooms();
    }

    public FlipbookRoomAbandonedCloseProcessResult closeAbandonedRooms(LocalDateTime now) {
        return flipbookAbandonedRoomCloseUseCase.closeAbandonedRooms(now);
    }
}
