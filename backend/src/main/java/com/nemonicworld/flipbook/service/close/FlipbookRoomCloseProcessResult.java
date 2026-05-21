package com.nemonicworld.flipbook.service.close;

/**
 * FINISHED 플립북 방 close 스캔 1회 실행 결과입니다.
 */
public record FlipbookRoomCloseProcessResult(int scannedRoomCount, int closedRoomCount) {
}
