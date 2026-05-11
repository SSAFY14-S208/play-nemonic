package com.nemonicworld.relay.repository;

import java.time.Duration;

public interface RelayFinalizationRetryRepository {

    int incrementFailureCount(String roomCode, Duration ttl);

    void clearFailureCount(String roomCode);
}
