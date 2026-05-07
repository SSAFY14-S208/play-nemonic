package com.nemonicworld.flipbook.service.timeout;

/**
 * 주기 스캔 1회에서 처리한 플립북 타임아웃 요약입니다.
 */
public record FlipbookTimeoutProcessResult(int scannedRoomCount, int processedRoomCount, int autoSubmittedCount) {
}
