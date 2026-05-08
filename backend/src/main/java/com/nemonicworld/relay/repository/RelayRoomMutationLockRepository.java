package com.nemonicworld.relay.repository;

import java.time.Duration;

public interface RelayRoomMutationLockRepository {

    boolean acquireRoomMutationLock(String roomCode, String token, Duration ttl);

    void releaseRoomMutationLock(String roomCode, String token);
}
