package com.nemonicworld.flipbook.repository;

import java.time.Duration;

public interface FlipbookFinalizationRetryRepository {

    int getFailureCount(String roomCode);

    int incrementFailureCount(String roomCode, Duration ttl);

    void clearFailureCount(String roomCode);
}
