package com.nemonicworld.flipbook.service;

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
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoundProgress;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
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

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FileUploadRepository fileUploadRepository;
    private final Duration autoSubmitGrace;

    public FlipbookFrameSubmitUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookRoomRepository flipbookRoomRepository, FlipbookRoomPolicy flipbookRoomPolicy,
        FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService, FileUploadRepository fileUploadRepository,
        @Value("${nemonic.flipbook.timeout.auto-submit-grace-ms:2000}") long autoSubmitGraceMs) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookFrameImageUrlResolver = flipbookFrameImageUrlResolver;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.fileUploadRepository = fileUploadRepository;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
    }

    /**
     * 현재 사용자가 배정받은 프레임에 업로드 완료된 파일을 연결하고, 라운드 완료 시 다음 단계로 진행합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookFrameSubmitResponse submitFrame(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        validateRound(round);
        validateRequest(request);
        String viewerUserUuid = viewerUser.getId().toString();
        FileUpload frameFile = resolveFrameFile(viewerUser.getId(), request.fileId());

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
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

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            FlipbookFrameAssignment submittedAssignment = submitAssignment(currentAssignment, frameFile, now);
            List<FlipbookFrameAssignment> updatedAssignments = replaceAssignment(roomState.assignments(),
                currentAssignment, submittedAssignment);
            FlipbookRoomState submittedRoomState = roomState.withAssignments(updatedAssignments, now);
            FlipbookRoundAdvanceResult advanceResult = flipbookRoomRoundAdvanceService
                .advanceRoundIfCompleted(submittedRoomState, round, now);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, advanceResult.roomState())) {
                flipbookInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());

                return createResponse(advanceResult.roomState(), submittedAssignment, false, participant,
                    advanceResult);
            }
        }

        throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
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

    private FlipbookFrameSubmitResponse createResponse(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        boolean alreadySubmitted, FlipbookRoomParticipant participant) {
        FlipbookRoundProgress progress = flipbookRoomRoundAdvanceService.calculateProgress(roomState,
            assignment.round());
        boolean allRoundsCompleted = roomState.status() == FlipbookRoomStatus.FINISHED;
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
