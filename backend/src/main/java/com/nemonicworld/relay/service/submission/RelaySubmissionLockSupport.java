package com.nemonicworld.relay.service.submission;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelaySubmissionLockSupport {

    private static final String SUBMISSION_IN_PROGRESS_MESSAGE = "이미 제출 처리 중입니다.";

    private static final int ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS = 5;
    private static final Duration ROOM_MUTATION_LOCK_RETRY_DELAY = Duration.ofMillis(50);

    private final RelaySubmissionLockRepository relaySubmissionLockRepository;
    private final RelayRoomMutationLockRepository relayRoomMutationLockRepository;
    private final Duration submitLockTtl;
    private final Duration roomMutationLockTtl;

    public RelaySubmissionLockSupport(RelaySubmissionLockRepository relaySubmissionLockRepository,
        RelayRoomMutationLockRepository relayRoomMutationLockRepository,
        @Value("${nemonic.relay.timeout.submit-lock-ttl-ms:10000}") long submitLockTtlMs,
        @Value("${nemonic.relay.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.relaySubmissionLockRepository = relaySubmissionLockRepository;
        this.relayRoomMutationLockRepository = relayRoomMutationLockRepository;
        this.submitLockTtl = Duration.ofMillis(Math.max(1L, submitLockTtlMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    public String createSubmissionLockToken(String viewerUserUuid) {
        return "token=%s,requestedAt=%s,userUuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), viewerUserUuid);
    }

    public String createRoomMutationLockToken(String owner, String viewerUserUuid) {
        return "token=%s,requestedAt=%s,owner=%s,userUuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, viewerUserUuid);
    }

    public boolean acquireSubmissionLock(String roomCode, RelayRoomAssignment assignment, String submissionLockToken) {
        boolean locked = relaySubmissionLockRepository.acquireSubmissionLock(roomCode, assignment.canvasIndex(),
            assignment.part(), assignment.assignedUserUuid(), submissionLockToken, submitLockTtl);
        if (!locked) {
            throw new ConflictException(SUBMISSION_IN_PROGRESS_MESSAGE);
        }

        return true;
    }

    public void releaseSubmissionLock(String roomCode, Integer canvasIndex, RelayDrawingPart part, String userUuid,
        String submissionLockToken) {
        relaySubmissionLockRepository.releaseSubmissionLock(roomCode, canvasIndex, part, userUuid, submissionLockToken);
    }

    public boolean acquireRoomMutationLock(String roomCode, String roomMutationLockToken) {
        for (int attempt = 0; attempt < ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS; attempt++) {
            boolean locked = relayRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
                roomMutationLockTtl);
            if (locked) {
                return true;
            }

            if (attempt < ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS - 1) {
                sleepBeforeRoomMutationLockRetry();
            }
        }

        RelayRoomEventLogger.apiWarn("relay_room_mutation_lock_busy",
            "relay submission failed because room mutation lock was busy",
            metadata("room_id", roomCode, "operation", "submission", "lock_ttl_ms", roomMutationLockTtl.toMillis()),
            null);
        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    public void releaseRoomMutationLock(String roomCode, String roomMutationLockToken) {
        relayRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
    }

    private void sleepBeforeRoomMutationLockRetry() {
        try {
            Thread.sleep(ROOM_MUTATION_LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        }
    }
}
