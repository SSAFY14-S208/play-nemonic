package com.nemonicworld.relay.service.finalization;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayFinalizationCleanupSupport {

    private final RelayResultStorage relayResultStorage;
    private final RelayFinalizationAttemptSupport relayFinalizationAttemptSupport;

    public RelayFinalizationCleanupSupport(RelayResultStorage relayResultStorage,
        RelayFinalizationAttemptSupport relayFinalizationAttemptSupport) {
        this.relayResultStorage = relayResultStorage;
        this.relayFinalizationAttemptSupport = relayFinalizationAttemptSupport;
    }

    public void cleanupCurrentAttemptResultObjects(String roomCode) {
        RelayFinalizationAttempt attempt = relayFinalizationAttemptSupport.currentAttempt();
        List<String> objectKeys = attempt == null
            ? List.of()
            : attempt.objectKeys().stream().filter(StringUtils::hasText).distinct().toList();
        if (objectKeys.isEmpty()) {
            return;
        }

        List<String> failedObjectKeys = new ArrayList<>();
        int deletedObjectCount = 0;
        for (String objectKey : objectKeys) {
            try {
                relayResultStorage.delete(objectKey);
                deletedObjectCount++;
            } catch (RuntimeException e) {
                failedObjectKeys.add(objectKey);
                RelayRoomEventLogger.apiWarn("relay_result_orphan_cleanup_failed",
                    "failed to clean orphan relay result object",
                    metadata("room_id", roomCode, "attempt_id", attempt.attemptId(), "object_key_hashes",
                        List.of(RelayRoomEventLogger.hash(objectKey)), "failed_object_count", 1, "error",
                        e.getClass().getSimpleName()),
                    e);
            }
        }

        RelayRoomEventLogger.apiBusiness("relay_result_orphan_cleanup_completed",
            metadata("room_id", roomCode, "attempt_id", attempt.attemptId(), "object_key_hashes",
                objectKeys.stream().map(RelayRoomEventLogger::hash).toList(), "deleted_object_count",
                deletedObjectCount, "failed_object_count", failedObjectKeys.size(), "result",
                failedObjectKeys.isEmpty() ? "success" : "partial_failure"));
    }
}
