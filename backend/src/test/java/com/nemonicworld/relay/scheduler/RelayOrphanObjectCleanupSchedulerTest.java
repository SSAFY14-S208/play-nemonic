package com.nemonicworld.relay.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.relay.service.cleanup.RelayOrphanObjectCleanupService;
import org.junit.jupiter.api.Test;

class RelayOrphanObjectCleanupSchedulerTest {

    @Test
    void cleanupOrphanObjectsDoesNothingWhenDisabled() {
        RelayOrphanObjectCleanupService service = org.mockito.Mockito.mock(RelayOrphanObjectCleanupService.class);
        RelayOrphanObjectCleanupScheduler scheduler = new RelayOrphanObjectCleanupScheduler(service, false);

        scheduler.cleanupOrphanObjects();

        verify(service, never()).cleanupOrphanObjects();
    }

    @Test
    void cleanupOrphanObjectsRunsWhenEnabled() {
        RelayOrphanObjectCleanupService service = org.mockito.Mockito.mock(RelayOrphanObjectCleanupService.class);
        RelayOrphanObjectCleanupScheduler scheduler = new RelayOrphanObjectCleanupScheduler(service, true);

        scheduler.cleanupOrphanObjects();

        verify(service).cleanupOrphanObjects();
    }
}
