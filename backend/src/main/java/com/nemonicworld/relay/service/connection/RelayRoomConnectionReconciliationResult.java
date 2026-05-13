package com.nemonicworld.relay.service.connection;

public record RelayRoomConnectionReconciliationResult(int scannedRoomCount, int reconciledRoomCount,
    int reconciledParticipantCount) {
}
