package com.nemonicworld.relay.service;

/**
 * 한 번의 릴레이 타임아웃 스캔 처리 요약을 담습니다.
 */
public record RelayTimeoutProcessResult(int scannedRoomCount, int processedRoomCount, int autoSubmittedCount) {
}
