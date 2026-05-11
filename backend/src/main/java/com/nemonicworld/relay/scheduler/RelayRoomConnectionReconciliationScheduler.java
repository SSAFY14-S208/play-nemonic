package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.connection.RelayRoomConnectionReconciliationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomConnectionReconciliationScheduler {

    private final RelayRoomConnectionReconciliationService relayRoomConnectionReconciliationService;
    private final boolean enabled;

    public RelayRoomConnectionReconciliationScheduler(
        RelayRoomConnectionReconciliationService relayRoomConnectionReconciliationService,
        @Value("${nemonic.relay.reconciliation.enabled:true}") boolean enabled) {
        this.relayRoomConnectionReconciliationService = relayRoomConnectionReconciliationService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${nemonic.relay.reconciliation.scan-delay-ms:30000}")
    public void reconcileConnections() {
        if (!enabled) {
            return;
        }
        relayRoomConnectionReconciliationService.reconcileConnections();
    }
}
