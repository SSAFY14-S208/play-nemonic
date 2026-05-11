package com.nemonicworld.flipbook.repository;

import java.time.Duration;

public interface FlipbookSubmissionLockRepository {

    boolean acquireSubmissionLock(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid,
        String token, Duration ttl);

    boolean isSubmissionLocked(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid);

    void releaseSubmissionLock(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid,
        String token);
}
