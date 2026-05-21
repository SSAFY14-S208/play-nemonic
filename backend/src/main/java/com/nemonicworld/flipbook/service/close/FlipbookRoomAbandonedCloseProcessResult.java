package com.nemonicworld.flipbook.service.close;

public record FlipbookRoomAbandonedCloseProcessResult(int scannedWaitingRoomCount, int closedWaitingRoomCount,
    int scannedPlayingRoomCount, int closedPlayingRoomCount) {
}
