package com.nemonicworld.relay.service.disconnect;

/**
 * 릴레이 게임 중 이탈 확정 스캔 처리 요약입니다.
 */
public record RelayDisconnectGraceProcessResult(int scannedRoomCount, int processedRoomCount,
    int droppedParticipantCount, int autoSubmittedCount) {
}
