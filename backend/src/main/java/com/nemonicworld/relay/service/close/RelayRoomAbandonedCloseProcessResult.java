package com.nemonicworld.relay.service.close;

public record RelayRoomAbandonedCloseProcessResult(int scannedWaitingRoomCount, int closedWaitingRoomCount,
    int scannedPlayingRoomCount, int closedPlayingRoomCount) {
}
