package com.nemonicworld.relay.service.finalization;

/**
 * 한 번의 최종화 스캔에서 처리한 방과 결과물 개수를 요약합니다.
 */
public record RelayFinalizationProcessResult(int scannedRoomCount, int processedRoomCount, int resultCount) {
}
