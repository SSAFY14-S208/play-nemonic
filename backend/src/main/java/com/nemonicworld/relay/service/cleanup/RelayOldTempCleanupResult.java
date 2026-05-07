package com.nemonicworld.relay.service.cleanup;

/**
 * 오래된 relay/tmp object fallback 정리 결과입니다.
 */
public record RelayOldTempCleanupResult(int scannedObjectCount, int deletedObjectCount) {
}
