package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.cleanup.RelayOrphanObjectCleanupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelayOrphanObjectCleanupScheduler {

    private static final String SCAN_DELAY_PROPERTY = "${nemonic.relay.orphan-cleanup.scan-delay-ms:3600000}";
    private static final String INITIAL_DELAY_PROPERTY = "${nemonic.relay.orphan-cleanup.initial-delay-ms:3600000}";

    private final RelayOrphanObjectCleanupService relayOrphanObjectCleanupService;
    private final boolean enabled;

    public RelayOrphanObjectCleanupScheduler(RelayOrphanObjectCleanupService relayOrphanObjectCleanupService,
        @Value("${nemonic.relay.orphan-cleanup.enabled:true}") boolean enabled) {
        this.relayOrphanObjectCleanupService = relayOrphanObjectCleanupService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = SCAN_DELAY_PROPERTY, initialDelayString = INITIAL_DELAY_PROPERTY)
    public void cleanupOrphanObjects() {
        if (!enabled) {
            return;
        }
        relayOrphanObjectCleanupService.cleanupOrphanObjects();
    }
}
