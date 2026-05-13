package com.nemonicworld.relay.service.cleanup;

public record RelayOrphanObjectCleanupResult(String target, int scannedCount, int deletedCount, int skippedCount,
    int failedCount) {
}
