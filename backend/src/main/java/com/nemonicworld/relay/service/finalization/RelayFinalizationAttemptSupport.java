package com.nemonicworld.relay.service.finalization;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayFinalizationAttemptRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayFinalizationAttemptSupport {

    private final RelayFinalizationAttemptRepository relayFinalizationAttemptRepository;
    private final ThreadLocal<RelayFinalizationAttempt> activeAttempt = new ThreadLocal<>();
    private final Duration attemptTtl;

    public RelayFinalizationAttemptSupport(RelayFinalizationAttemptRepository relayFinalizationAttemptRepository,
        @Value("${nemonic.relay.finalization.attempt-ttl-hours:24}") long attemptTtlHours) {
        this.relayFinalizationAttemptRepository = relayFinalizationAttemptRepository;
        this.attemptTtl = Duration.ofHours(Math.max(1L, attemptTtlHours));
    }

    public void clearContext() {
        activeAttempt.remove();
    }

    public void saveActiveAttempt(RelayFinalizationAttempt attempt) {
        activeAttempt.set(attempt);
        relayFinalizationAttemptRepository.save(attempt, attemptTtl);
    }

    public void clearActiveAttempt(String roomCode, String attemptId) {
        activeAttempt.remove();
        try {
            relayFinalizationAttemptRepository.clear(roomCode, attemptId);
        } catch (RuntimeException e) {
            RelayRoomEventLogger.apiWarn("relay_finalization_attempt_clear_failed",
                "failed to clear relay finalization attempt marker",
                metadata("room_id", roomCode, "attempt_id", attemptId), e);
        }
    }

    public void registerAttemptObjectKey(String objectKey) {
        RelayFinalizationAttempt attempt = activeAttempt.get();
        if (attempt == null || !StringUtils.hasText(objectKey)) {
            return;
        }

        saveActiveAttempt(attempt.addObjectKey(objectKey));
    }

    public String currentAttemptId() {
        RelayFinalizationAttempt attempt = activeAttempt.get();

        return attempt == null ? null : attempt.attemptId();
    }

    RelayFinalizationAttempt currentAttempt() {
        return activeAttempt.get();
    }
}
