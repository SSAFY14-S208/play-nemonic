package com.nemonicworld.relay.service.close;

/**
 * FINISHED 방 close 스캔 1회 실행 결과입니다.
 */
public record RelayRoomCloseProcessResult(int scannedRoomCount, int closedRoomCount) {
}
