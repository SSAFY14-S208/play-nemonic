package com.nemonicworld.relay.service.finalization;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayFinalizationRetryRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RelayFinalizationFailureSupport {

    private final RelayFinalizationRetryRepository relayFinalizationRetryRepository;
    private final RelayFinalizationAttemptSupport relayFinalizationAttemptSupport;
    private final ThreadLocal<RelayFinalizationFailureContext> failureContext = new ThreadLocal<>();

    public RelayFinalizationFailureSupport(RelayFinalizationRetryRepository relayFinalizationRetryRepository,
        RelayFinalizationAttemptSupport relayFinalizationAttemptSupport) {
        this.relayFinalizationRetryRepository = relayFinalizationRetryRepository;
        this.relayFinalizationAttemptSupport = relayFinalizationAttemptSupport;
    }

    public void clearContext() {
        failureContext.remove();
    }

    public void recordFailure(String roomCode, String stage, UUID artifactId, RuntimeException error) {
        failureContext.set(
            new RelayFinalizationFailureContext(stage, artifactId, relayFinalizationAttemptSupport.currentAttemptId()));
    }

    public RelayFinalizationFailureResult handleFailure(String roomCode, RuntimeException error, int maxRetryCount) {
        RelayFinalizationFailureContext context = failureContext.get();
        failureContext.remove();

        int retryCount = relayFinalizationRetryRepository.incrementFailureCount(roomCode,
            RelayRoomRepository.ROOM_STATE_TTL);
        String stage = context == null ? "process" : context.stage();
        UUID artifactId = context == null ? null : context.artifactId();
        String attemptId = context == null ? null : context.attemptId();
        RelayRoomEventLogger.apiWarn("relay_finalization_attempt_failed", "failed relay finalization attempt",
            metadata("room_id", roomCode, "attempt_id", attemptId, "retry_count", retryCount, "stage", stage, "error",
                error.getClass().getSimpleName()),
            error);
        RelayRoomEventLogger.apiWarn("relay_finalization_failed", "failed to finalize relay room",
            metadata("room_id", roomCode, "stage", stage, "artifact_id", artifactId, "attempt_id", attemptId,
                "operation", "finalization", "retry_count", retryCount, "max_retry_count", maxRetryCount),
            error);

        return new RelayFinalizationFailureResult(retryCount, stage, artifactId, attemptId);
    }

    public int currentFailureCount(String roomCode) {
        return relayFinalizationRetryRepository.getFailureCount(roomCode);
    }

    public void clearFailureCount(String roomCode) {
        relayFinalizationRetryRepository.clearFailureCount(roomCode);
    }

    public record RelayFinalizationFailureResult(int retryCount, String stage, UUID artifactId, String attemptId) {
    }

    private record RelayFinalizationFailureContext(String stage, UUID artifactId, String attemptId) {
    }
}
