package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FlipbookSubmissionLockSupport {

    private static final String ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE = "동시 프레임 제출 요청이 많아 플립북 프레임을 "
        + "저장하지 못했습니다. 다시 시도해주세요.";
    private static final String SUBMISSION_IN_PROGRESS_MESSAGE = "이미 제출 처리 중입니다.";
    private static final int ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS = 5;
    private static final Duration ROOM_MUTATION_LOCK_RETRY_DELAY = Duration.ofMillis(50);

    private final FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;
    private final FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;
    private final Duration submitLockTtl;
    private final Duration roomMutationLockTtl;

    public FlipbookSubmissionLockSupport(FlipbookSubmissionLockRepository flipbookSubmissionLockRepository,
        FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository,
        @Value("${nemonic.flipbook.timeout.submit-lock-ttl-ms:10000}") long submitLockTtlMs,
        @Value("${nemonic.flipbook.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.flipbookSubmissionLockRepository = flipbookSubmissionLockRepository;
        this.flipbookRoomMutationLockRepository = flipbookRoomMutationLockRepository;
        this.submitLockTtl = Duration.ofMillis(Math.max(1L, submitLockTtlMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    public String createSubmissionLockToken(String viewerUserUuid) {
        return "token=%s,requestedAt=%s,userUuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), viewerUserUuid);
    }

    public String createRoomMutationLockToken(String owner, String viewerUserUuid) {
        return "token=%s,requestedAt=%s,owner=%s,uuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, viewerUserUuid);
    }

    public boolean acquireSubmissionLock(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        String submissionLockToken) {
        boolean locked = flipbookSubmissionLockRepository.acquireSubmissionLock(roomState.roomCode(),
            assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(), assignment.assignedUserUuid(),
            submissionLockToken, submitLockTtl);
        if (!locked) {
            throw new ConflictException(SUBMISSION_IN_PROGRESS_MESSAGE);
        }

        return true;
    }

    public void releaseSubmissionLock(String roomCode, int flipbookIndex, int frameIndex, int round, String userUuid,
        String submissionLockToken) {
        flipbookSubmissionLockRepository.releaseSubmissionLock(roomCode, flipbookIndex, frameIndex, round, userUuid,
            submissionLockToken);
    }

    public boolean acquireRoomMutationLock(String roomCode, String roomMutationLockToken) {
        for (int attempt = 0; attempt < ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS; attempt++) {
            boolean locked = flipbookRoomMutationLockRepository.acquireRoomMutationLock(roomCode, roomMutationLockToken,
                roomMutationLockTtl);
            if (locked) {
                return true;
            }

            if (attempt < ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS - 1) {
                sleepBeforeRoomMutationLockRetry();
            }
        }

        throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
    }

    public void releaseRoomMutationLock(String roomCode, String roomMutationLockToken) {
        flipbookRoomMutationLockRepository.releaseRoomMutationLock(roomCode, roomMutationLockToken);
    }

    private void sleepBeforeRoomMutationLockRetry() {
        try {
            Thread.sleep(ROOM_MUTATION_LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
        }
    }
}
