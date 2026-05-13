package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoundProgress;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 게임 중 현재 라운드 프레임 제출 유스케이스입니다.
 */
@Service
public class FlipbookFrameSubmitUseCase {

    private static final String INVALID_ROUND_MESSAGE = "유효하지 않은 라운드입니다.";
    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";
    private static final String INVALID_FILE_ID_MESSAGE = "유효하지 않은 fileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "업로드 완료된 파일만 제출할 수 있습니다.";
    private static final String FILE_UPLOAD_PURPOSE_CONFLICT_MESSAGE = "플립북 프레임 파일만 제출할 수 있습니다.";
    private static final String ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE = "동시 프레임 제출 요청이 많아 플립북 프레임을 "
        + "저장하지 못했습니다. 다시 시도해주세요.";
    private static final String SUBMISSION_IN_PROGRESS_MESSAGE = "이미 제출 처리 중입니다.";
    private static final int ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS = 5;
    private static final Duration ROOM_MUTATION_LOCK_RETRY_DELAY = Duration.ofMillis(50);

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FileUploadRepository fileUploadRepository;
    private final Duration autoSubmitGrace;
    private final FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;
    private final FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;
    private final Duration submitLockTtl;
    private final Duration roomMutationLockTtl;

    public FlipbookFrameSubmitUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookRoomRepository flipbookRoomRepository, FlipbookRoomPolicy flipbookRoomPolicy,
        FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService, FileUploadRepository fileUploadRepository,
        FlipbookSubmissionLockRepository flipbookSubmissionLockRepository,
        FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository,
        @Value("${nemonic.flipbook.timeout.auto-submit-grace-ms:5000}") long autoSubmitGraceMs,
        @Value("${nemonic.flipbook.timeout.submit-lock-ttl-ms:10000}") long submitLockTtlMs,
        @Value("${nemonic.flipbook.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookFrameImageUrlResolver = flipbookFrameImageUrlResolver;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.fileUploadRepository = fileUploadRepository;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
        this.flipbookSubmissionLockRepository = flipbookSubmissionLockRepository;
        this.flipbookRoomMutationLockRepository = flipbookRoomMutationLockRepository;
        this.submitLockTtl = Duration.ofMillis(Math.max(1L, submitLockTtlMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    /**
     * 현재 사용자가 배정받은 프레임에 업로드 완료된 파일을 연결하고, 라운드 완료 시 다음 단계로 진행합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookFrameSubmitResponse submitFrame(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        try {
            return submitFrameInternal(userUuidValue, roomCodeValue, round, request);
        } catch (RuntimeException e) {
            logFrameSubmissionRejected(userUuidValue, roomCodeValue, round, request, e);
            throw e;
        }
    }

    private FlipbookFrameSubmitResponse submitFrameInternal(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        validateRound(round);
        validateRequest(request);
        String viewerUserUuid = viewerUser.getId().toString();
        FileUpload frameFile = resolveFrameFile(viewerUser.getId(), request.fileId());
        String submissionLockToken = createSubmissionLockToken(viewerUserUuid);
        String roomMutationLockToken = createRoomMutationLockToken("submission", viewerUserUuid);
        boolean submissionLocked = false;
        boolean roomMutationLocked = false;

        try {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            flipbookRoomPolicy.validateNotDropped(roomState, participant.userUuid());
            flipbookRoomPolicy.validateAssignmentQueryableRoom(roomState);

            Optional<FlipbookFrameAssignment> requestedAssignment = findRequestedAssignment(roomState, viewerUserUuid,
                round, request.flipbookIndex(), request.frameIndex());
            if (requestedAssignment.isPresent()) {
                FlipbookFrameAssignment assignment = requestedAssignment.get();
                if (assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED) {
                    return createResponse(roomState, assignment, true, participant);
                }
                if (assignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()) {
                    throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                }
            }

            FlipbookFrameAssignment currentAssignment = flipbookRoomPolicy.requireCurrentAssignment(roomState,
                viewerUserUuid);
            validateAssignmentMatches(roomState, currentAssignment, round, request.flipbookIndex(),
                request.frameIndex());

            if (currentAssignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED
                || currentAssignment.autoSubmitted()) {
                throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
            }

            validateDeadline(roomState.roundDeadlineAt());
            submissionLocked = acquireSubmissionLock(roomState, currentAssignment, submissionLockToken);

            roomMutationLocked = acquireRoomMutationLock(roomCodeValue, roomMutationLockToken);

            for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
                FlipbookRoomState latestRoomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
                FlipbookRoomParticipant latestParticipant = flipbookRoomPolicy.requireParticipant(latestRoomState,
                    viewerUserUuid);
                flipbookRoomPolicy.validateNotDropped(latestRoomState, latestParticipant.userUuid());
                flipbookRoomPolicy.validateAssignmentQueryableRoom(latestRoomState);

                Optional<FlipbookFrameAssignment> latestRequestedAssignment = findRequestedAssignment(latestRoomState,
                    viewerUserUuid, round, request.flipbookIndex(), request.frameIndex());
                if (latestRequestedAssignment.isPresent()) {
                    FlipbookFrameAssignment assignment = latestRequestedAssignment.get();
                    if (assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED) {
                        return createResponse(latestRoomState, assignment, true, latestParticipant);
                    }
                    if (assignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED
                        || assignment.autoSubmitted()) {
                        throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                    }
                }

                FlipbookFrameAssignment latestCurrentAssignment = flipbookRoomPolicy
                    .requireCurrentAssignment(latestRoomState, viewerUserUuid);
                validateAssignmentMatches(latestRoomState, latestCurrentAssignment, round, request.flipbookIndex(),
                    request.frameIndex());

                if (latestCurrentAssignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED
                    || latestCurrentAssignment.autoSubmitted()) {
                    throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                }

                validateDeadline(latestRoomState.roundDeadlineAt());

                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                FlipbookFrameAssignment submittedAssignment = submitAssignment(latestCurrentAssignment, frameFile, now);
                List<FlipbookFrameAssignment> updatedAssignments = replaceAssignment(latestRoomState.assignments(),
                    latestCurrentAssignment, submittedAssignment);
                FlipbookRoomState submittedRoomState = latestRoomState.withAssignments(updatedAssignments, now);
                FlipbookRoundAdvanceResult advanceResult = flipbookRoomRoundAdvanceService
                    .advanceRoundIfCompleted(submittedRoomState, round, now);

                if (flipbookRoomRepository.saveIfUnchanged(latestRoomState, advanceResult.roomState())) {
                    flipbookInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                    logFrameSubmitted(advanceResult, submittedAssignment, latestParticipant, frameFile);

                    return createResponse(advanceResult.roomState(), submittedAssignment, false, latestParticipant,
                        advanceResult);
                }
            }

            throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
        } finally {
            if (roomMutationLocked) {
                flipbookRoomMutationLockRepository.releaseRoomMutationLock(roomCodeValue, roomMutationLockToken);
            }
            if (submissionLocked) {
                flipbookSubmissionLockRepository.releaseSubmissionLock(roomCodeValue, request.flipbookIndex(),
                    request.frameIndex(), round, viewerUserUuid, submissionLockToken);
            }
        }
    }

    private void logFrameSubmissionRejected(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request, RuntimeException e) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_submission_rejected",
            metadata("room_id", roomCodeValue, "uuid", safeUuid(userUuidValue), "round", round, "flipbook_index",
                request == null ? null : request.flipbookIndex(), "frame_index",
                request == null ? null : request.frameIndex(), "file_id", request == null ? null : request.fileId(),
                "result", "rejected", "reason_code", e.getClass().getSimpleName()));
    }

    private String safeUuid(String userUuidValue) {
        if (!StringUtils.hasText(userUuidValue)) {
            return null;
        }

        try {
            return UUID.fromString(userUuidValue).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void logFrameSubmitted(FlipbookRoundAdvanceResult advanceResult, FlipbookFrameAssignment assignment,
        FlipbookRoomParticipant participant, FileUpload frameFile) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_frame_submitted",
            metadata("room_id", advanceResult.roomState().roomCode(), "uuid", participant.userUuid(), "round",
                assignment.round(), "flipbook_index", assignment.flipbookIndex(), "frame_index",
                assignment.frameIndex(), "file_id", frameFile.getId(), "object_key_hash",
                FlipbookRoomEventLogger.hash(frameFile.getObjectKey()), "submitted_count",
                advanceResult.progress().submittedCount(), "total_count", advanceResult.progress().totalCount(),
                "room_status", advanceResult.roomState().status()));
        if (advanceResult.allRoundsCompleted()) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_all_rounds_completed",
                metadata("room_id", advanceResult.roomState().roomCode(), "room_status",
                    advanceResult.roomState().status(), "total_rounds", advanceResult.roomState().totalRounds()));
        } else if (advanceResult.advanced()) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_round_started",
                metadata("room_id", advanceResult.roomState().roomCode(), "round", advanceResult.nextRound(),
                    "round_deadline_at", advanceResult.nextRoundDeadlineAt()));
        }
    }

    private void validateRound(int round) {
        if (round <= 0) {
            throw new BadRequestException(INVALID_ROUND_MESSAGE);
        }
    }

    private void validateRequest(FlipbookFrameSubmitRequest request) {
        if (request == null || request.flipbookIndex() == null || request.frameIndex() == null
            || !StringUtils.hasText(request.fileId())) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    private FileUpload resolveFrameFile(UUID viewerUserUuid, String fileIdValue) {
        UUID fileId = parseFileId(fileIdValue);
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(viewerUserUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isUploaded()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }

        if (fileUpload.getPurpose() != FileUploadPurpose.FLIPBOOK) {
            throw new ConflictException(FILE_UPLOAD_PURPOSE_CONFLICT_MESSAGE);
        }

        return fileUpload;
    }

    private UUID parseFileId(String fileIdValue) {
        if (!StringUtils.hasText(fileIdValue)) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }

        try {
            return UUID.fromString(fileIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }
    }

    private Optional<FlipbookFrameAssignment> findRequestedAssignment(FlipbookRoomState roomState,
        String viewerUserUuid, int round, int flipbookIndex, int frameIndex) {
        return roomState.assignments().stream().filter(assignment -> assignment.round() == round)
            .filter(assignment -> assignment.flipbookIndex() == flipbookIndex)
            .filter(assignment -> assignment.frameIndex() == frameIndex)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst();
    }

    private void validateAssignmentMatches(FlipbookRoomState roomState, FlipbookFrameAssignment currentAssignment,
        int requestedRound, int requestedFlipbookIndex, int requestedFrameIndex) {
        if (roomState.currentRound() == null || roomState.currentRound() != requestedRound
            || currentAssignment.round() != requestedRound
            || currentAssignment.flipbookIndex() != requestedFlipbookIndex
            || currentAssignment.frameIndex() != requestedFrameIndex) {
            throw new ConflictException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    private void validateDeadline(LocalDateTime roundDeadlineAt) {
        if (roundDeadlineAt == null) {
            return;
        }

        LocalDateTime expiresAt = roundDeadlineAt.plus(autoSubmitGrace);
        if (!expiresAt.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictException(SUBMISSION_EXPIRED_MESSAGE);
        }
    }

    private FlipbookFrameAssignment submitAssignment(FlipbookFrameAssignment currentAssignment, FileUpload frameFile,
        LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(currentAssignment.flipbookIndex(), currentAssignment.frameIndex(),
            currentAssignment.round(), currentAssignment.assignedUserUuid(), FlipbookFrameAssignmentStatus.SUBMITTED,
            frameFile.getId().toString(), frameFile.getObjectKey(), false, false, submittedAt);
    }

    private List<FlipbookFrameAssignment> replaceAssignment(List<FlipbookFrameAssignment> assignments,
        FlipbookFrameAssignment currentAssignment, FlipbookFrameAssignment submittedAssignment) {
        List<FlipbookFrameAssignment> updatedAssignments = new ArrayList<>(assignments.size());
        for (FlipbookFrameAssignment assignment : assignments) {
            if (assignment.flipbookIndex() == currentAssignment.flipbookIndex()
                && assignment.frameIndex() == currentAssignment.frameIndex()
                && assignment.round() == currentAssignment.round()
                && assignment.assignedUserUuid().equals(currentAssignment.assignedUserUuid())) {
                updatedAssignments.add(submittedAssignment);
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return updatedAssignments;
    }

    private boolean acquireSubmissionLock(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        String submissionLockToken) {
        boolean locked = flipbookSubmissionLockRepository.acquireSubmissionLock(roomState.roomCode(),
            assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(), assignment.assignedUserUuid(),
            submissionLockToken, submitLockTtl);
        if (!locked) {
            throw new ConflictException(SUBMISSION_IN_PROGRESS_MESSAGE);
        }

        return true;
    }

    private String createSubmissionLockToken(String viewerUserUuid) {
        return "token=%s,requestedAt=%s,userUuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), viewerUserUuid);
    }

    private boolean acquireRoomMutationLock(String roomCode, String roomMutationLockToken) {
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

    private void sleepBeforeRoomMutationLockRetry() {
        try {
            Thread.sleep(ROOM_MUTATION_LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
        }
    }

    private String createRoomMutationLockToken(String owner, String viewerUserUuid) {
        return "token=%s,requestedAt=%s,owner=%s,uuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, viewerUserUuid);
    }

    private FlipbookFrameSubmitResponse createResponse(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        boolean alreadySubmitted, FlipbookRoomParticipant participant) {
        FlipbookRoundProgress progress = flipbookRoomRoundAdvanceService.calculateProgress(roomState,
            assignment.round());
        boolean allRoundsCompleted = roomState.status() == FlipbookRoomStatus.FINALIZING
            || roomState.status() == FlipbookRoomStatus.FINISHED;
        boolean advanced = allRoundsCompleted
            || roomState.currentRound() != null && roomState.currentRound() > assignment.round();
        Integer nextRound = advanced && roomState.status() == FlipbookRoomStatus.PLAYING
            ? roomState.currentRound()
            : null;
        LocalDateTime nextRoundStartedAt = nextRound == null ? null : roomState.roundStartedAt();
        LocalDateTime nextRoundDeadlineAt = nextRound == null ? null : roomState.roundDeadlineAt();

        return createResponse(roomState, assignment, alreadySubmitted, participant, new FlipbookRoundAdvanceResult(
            roomState, advanced, nextRound, nextRoundStartedAt, nextRoundDeadlineAt, allRoundsCompleted, progress));
    }

    private FlipbookFrameSubmitResponse createResponse(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        boolean alreadySubmitted, FlipbookRoomParticipant participant, FlipbookRoundAdvanceResult advanceResult) {
        String frameUrl = flipbookFrameImageUrlResolver.resolve(assignment.objectKey());

        return FlipbookFrameSubmitResponse.from(roomState.roomCode(), assignment, frameUrl, alreadySubmitted,
            advanceResult.progress().currentRoundCompleted(), advanceResult.progress().submittedCount(),
            advanceResult.progress().totalCount(), advanceResult.advanced(), advanceResult.nextRound(),
            advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt(), advanceResult.allRoundsCompleted(),
            roomState.status(), participant.userUuid(), participant.nickname());
    }
}
