package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.service.finalization.RelayFinalizationAttempt;
import java.time.Duration;

public interface RelayFinalizationAttemptRepository {

    void save(RelayFinalizationAttempt attempt, Duration ttl);

    void clear(String roomCode, String attemptId);

    boolean containsObjectKey(String objectKey, int scanLimit);
}
