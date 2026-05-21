package com.nemonicworld.relay.service.finalization;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record RelayFinalizationAttempt(String roomCode, String attemptId, List<String> objectKeys,
    LocalDateTime startedAt) {

    public RelayFinalizationAttempt {
        objectKeys = objectKeys == null ? List.of() : List.copyOf(objectKeys);
    }

    public static RelayFinalizationAttempt start(String roomCode, String attemptId, LocalDateTime startedAt) {
        return new RelayFinalizationAttempt(roomCode, attemptId, List.of(), startedAt);
    }

    public RelayFinalizationAttempt addObjectKey(String objectKey) {
        List<String> updatedObjectKeys = new ArrayList<>(objectKeys);
        updatedObjectKeys.add(objectKey);

        return new RelayFinalizationAttempt(roomCode, attemptId, updatedObjectKeys, startedAt);
    }

    public RelayFinalizationAttempt addObjectKeys(String originalObjectKey, String thumbnailObjectKey) {
        List<String> updatedObjectKeys = new ArrayList<>(objectKeys);
        updatedObjectKeys.add(originalObjectKey);
        updatedObjectKeys.add(thumbnailObjectKey);

        return new RelayFinalizationAttempt(roomCode, attemptId, updatedObjectKeys, startedAt);
    }
}
