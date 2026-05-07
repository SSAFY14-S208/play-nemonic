package com.nemonicworld.flipbook.service.disconnect;

/**
 * 플립북 게임 중 이탈 확정 스캔 처리 요약입니다.
 */
public record FlipbookDisconnectGraceProcessResult(int scannedRoomCount, int processedRoomCount,
    int droppedParticipantCount) {
}
