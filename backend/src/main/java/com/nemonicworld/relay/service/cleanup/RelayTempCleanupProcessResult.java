package com.nemonicworld.relay.service.cleanup;

/**
 * CLOSED 방 임시 파일 정리 스캔 한 번의 처리 결과입니다.
 */
public record RelayTempCleanupProcessResult(int scannedRoomCount, int cleanedRoomCount, int deletedObjectCount) {
}
