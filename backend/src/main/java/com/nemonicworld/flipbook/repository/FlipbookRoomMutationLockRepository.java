package com.nemonicworld.flipbook.repository;

import java.time.Duration;

public interface FlipbookRoomMutationLockRepository {

    boolean acquireRoomMutationLock(String roomCode, String token, Duration ttl);

    void releaseRoomMutationLock(String roomCode, String token);
}
