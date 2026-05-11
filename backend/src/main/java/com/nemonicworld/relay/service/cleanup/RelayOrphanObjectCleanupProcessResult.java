package com.nemonicworld.relay.service.cleanup;

public record RelayOrphanObjectCleanupProcessResult(RelayOrphanObjectCleanupResult tempResult,
    RelayOrphanObjectCleanupResult resultResult) {
}
