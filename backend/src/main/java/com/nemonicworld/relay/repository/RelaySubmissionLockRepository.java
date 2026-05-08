package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.time.Duration;

public interface RelaySubmissionLockRepository {

    boolean acquireSubmissionLock(String roomCode, int canvasIndex, RelayDrawingPart part, String userUuid,
        String token, Duration ttl);

    boolean isSubmissionLocked(String roomCode, int canvasIndex, RelayDrawingPart part, String userUuid);

    void releaseSubmissionLock(String roomCode, int canvasIndex, RelayDrawingPart part, String userUuid, String token);
}
