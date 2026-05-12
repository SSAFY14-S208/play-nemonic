package com.nemonicworld.flipbook.scheduler;

import com.nemonicworld.flipbook.service.close.FlipbookRoomAbandonedCloseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlipbookRoomAbandonedCloseScheduler {

    private final FlipbookRoomAbandonedCloseService flipbookRoomAbandonedCloseService;
    private final boolean enabled;

    public FlipbookRoomAbandonedCloseScheduler(FlipbookRoomAbandonedCloseService flipbookRoomAbandonedCloseService,
        @Value("${nemonic.flipbook.abandoned-close.enabled:true}") boolean enabled) {
        this.flipbookRoomAbandonedCloseService = flipbookRoomAbandonedCloseService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${nemonic.flipbook.abandoned-close.scan-delay-ms:30000}")
    public void closeAbandonedRooms() {
        if (!enabled) {
            return;
        }
        flipbookRoomAbandonedCloseService.closeAbandonedRooms();
    }
}
