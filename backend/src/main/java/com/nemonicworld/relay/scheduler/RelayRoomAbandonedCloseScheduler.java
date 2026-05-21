package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.close.RelayRoomAbandonedCloseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomAbandonedCloseScheduler {

    private final RelayRoomAbandonedCloseService relayRoomAbandonedCloseService;
    private final boolean enabled;

    public RelayRoomAbandonedCloseScheduler(RelayRoomAbandonedCloseService relayRoomAbandonedCloseService,
        @Value("${nemonic.relay.abandoned-close.enabled:true}") boolean enabled) {
        this.relayRoomAbandonedCloseService = relayRoomAbandonedCloseService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${nemonic.relay.abandoned-close.scan-delay-ms:30000}")
    public void closeAbandonedRooms() {
        if (!enabled) {
            return;
        }
        relayRoomAbandonedCloseService.closeAbandonedRooms();
    }
}
