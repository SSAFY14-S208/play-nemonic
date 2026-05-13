package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.service.finalization.RelayFinalizationAttempt;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;

public interface RelayFinalizationAttemptRepository {

    void save(RelayFinalizationAttempt attempt, Duration ttl);

    void clear(String roomCode, String attemptId);

    boolean containsObjectKey(String objectKey, int scanLimit);

    Set<String> findReferencedObjectKeys(Collection<String> objectKeys, int scanLimit);
}
