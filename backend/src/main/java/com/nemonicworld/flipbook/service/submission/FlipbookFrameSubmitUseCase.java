package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlipbookFrameSubmitUseCase {

    private static final String ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE = "동시 프레임 제출 요청이 많아 플립북 프레임을 "
        + "저장하지 못했습니다. 다시 시도해주세요.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;
    private final FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService;
    private final FlipbookFrameFileSupport flipbookFrameFileSupport;
    private final FlipbookSubmissionLockSupport flipbookSubmissionLockSupport;
    private final FlipbookFrameAssignmentSupport flipbookFrameAssignmentSupport;
    private final FlipbookFrameSubmissionResponseSupport flipbookFrameSubmissionResponseSupport;
    private final FlipbookFrameSubmissionEventSupport flipbookFrameSubmissionEventSupport;

    public FlipbookFrameSubmitUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookRoomRepository flipbookRoomRepository, FlipbookRoomPolicy flipbookRoomPolicy,
        FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService,
        FlipbookRoomRoundAdvanceService flipbookRoomRoundAdvanceService,
        FlipbookFrameFileSupport flipbookFrameFileSupport, FlipbookSubmissionLockSupport flipbookSubmissionLockSupport,
        FlipbookFrameAssignmentSupport flipbookFrameAssignmentSupport,
        FlipbookFrameSubmissionResponseSupport flipbookFrameSubmissionResponseSupport,
        FlipbookFrameSubmissionEventSupport flipbookFrameSubmissionEventSupport) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookInviteMetadataSyncService = flipbookInviteMetadataSyncService;
        this.flipbookRoomRoundAdvanceService = flipbookRoomRoundAdvanceService;
        this.flipbookFrameFileSupport = flipbookFrameFileSupport;
        this.flipbookSubmissionLockSupport = flipbookSubmissionLockSupport;
        this.flipbookFrameAssignmentSupport = flipbookFrameAssignmentSupport;
        this.flipbookFrameSubmissionResponseSupport = flipbookFrameSubmissionResponseSupport;
        this.flipbookFrameSubmissionEventSupport = flipbookFrameSubmissionEventSupport;
    }

    @Transactional(readOnly = true)
    public FlipbookFrameSubmitResponse submitFrame(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        try {
            return submitFrameInternal(userUuidValue, roomCodeValue, round, request);
        } catch (RuntimeException e) {
            flipbookFrameSubmissionEventSupport.logFrameSubmissionRejected(userUuidValue, roomCodeValue, round, request,
                e);
            throw e;
        }
    }

    private FlipbookFrameSubmitResponse submitFrameInternal(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        flipbookFrameAssignmentSupport.validateRound(round);
        flipbookFrameAssignmentSupport.validateRequest(request);
        String viewerUserUuid = viewerUser.getId().toString();
        FileUpload frameFile = flipbookFrameFileSupport.resolveFrameFile(viewerUser.getId(), request.fileId());
        String submissionLockToken = flipbookSubmissionLockSupport.createSubmissionLockToken(viewerUserUuid);
        String roomMutationLockToken = flipbookSubmissionLockSupport.createRoomMutationLockToken("submission",
            viewerUserUuid);
        boolean submissionLocked = false;
        boolean roomMutationLocked = false;

        try {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            flipbookRoomPolicy.validateNotDropped(roomState, participant.userUuid());
            flipbookRoomPolicy.validateAssignmentQueryableRoom(roomState);

            Optional<FlipbookFrameAssignment> requestedAssignment = flipbookFrameAssignmentSupport
                .findRequestedAssignment(roomState, viewerUserUuid, round, request.flipbookIndex(),
                    request.frameIndex());
            if (requestedAssignment.isPresent()) {
                FlipbookFrameAssignment assignment = requestedAssignment.get();
                if (assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED) {
                    return flipbookFrameSubmissionResponseSupport.createResponse(roomState, assignment, true,
                        participant);
                }
                flipbookFrameAssignmentSupport.validateNotAutoSubmitted(assignment);
            }

            FlipbookFrameAssignment currentAssignment = flipbookRoomPolicy.requireCurrentAssignment(roomState,
                viewerUserUuid);
            flipbookFrameAssignmentSupport.validateAssignmentMatches(roomState, currentAssignment, round,
                request.flipbookIndex(), request.frameIndex());
            flipbookFrameAssignmentSupport.validateNotAutoSubmitted(currentAssignment);
            flipbookFrameAssignmentSupport.validateDeadline(roomState.roundDeadlineAt());
            submissionLocked = flipbookSubmissionLockSupport.acquireSubmissionLock(roomState, currentAssignment,
                submissionLockToken);

            roomMutationLocked = flipbookSubmissionLockSupport.acquireRoomMutationLock(roomCodeValue,
                roomMutationLockToken);

            for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
                FlipbookRoomState latestRoomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
                FlipbookRoomParticipant latestParticipant = flipbookRoomPolicy.requireParticipant(latestRoomState,
                    viewerUserUuid);
                flipbookRoomPolicy.validateNotDropped(latestRoomState, latestParticipant.userUuid());
                flipbookRoomPolicy.validateAssignmentQueryableRoom(latestRoomState);

                Optional<FlipbookFrameAssignment> latestRequestedAssignment = flipbookFrameAssignmentSupport
                    .findRequestedAssignment(latestRoomState, viewerUserUuid, round, request.flipbookIndex(),
                        request.frameIndex());
                if (latestRequestedAssignment.isPresent()) {
                    FlipbookFrameAssignment assignment = latestRequestedAssignment.get();
                    if (assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED) {
                        return flipbookFrameSubmissionResponseSupport.createResponse(latestRoomState, assignment, true,
                            latestParticipant);
                    }
                    flipbookFrameAssignmentSupport.validateNotAutoSubmitted(assignment);
                }

                FlipbookFrameAssignment latestCurrentAssignment = flipbookRoomPolicy
                    .requireCurrentAssignment(latestRoomState, viewerUserUuid);
                flipbookFrameAssignmentSupport.validateAssignmentMatches(latestRoomState, latestCurrentAssignment,
                    round, request.flipbookIndex(), request.frameIndex());
                flipbookFrameAssignmentSupport.validateNotAutoSubmitted(latestCurrentAssignment);
                flipbookFrameAssignmentSupport.validateDeadline(latestRoomState.roundDeadlineAt());

                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                FlipbookFrameAssignment submittedAssignment = flipbookFrameAssignmentSupport
                    .submitAssignment(latestCurrentAssignment, frameFile, now);
                FlipbookRoomState submittedRoomState = latestRoomState.withAssignments(flipbookFrameAssignmentSupport
                    .replaceAssignment(latestRoomState.assignments(), latestCurrentAssignment, submittedAssignment),
                    now);
                FlipbookRoundAdvanceResult advanceResult = flipbookRoomRoundAdvanceService
                    .advanceRoundIfCompleted(submittedRoomState, round, now);

                if (flipbookRoomRepository.saveIfUnchanged(latestRoomState, advanceResult.roomState())) {
                    flipbookInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                    flipbookFrameSubmissionEventSupport.logFrameSubmitted(advanceResult, submittedAssignment,
                        latestParticipant, frameFile);

                    return flipbookFrameSubmissionResponseSupport.createResponse(advanceResult.roomState(),
                        submittedAssignment, false, latestParticipant, advanceResult);
                }
            }

            throw new ConflictException(ROOM_FRAME_SUBMIT_UPDATE_CONFLICT_MESSAGE);
        } finally {
            if (roomMutationLocked) {
                flipbookSubmissionLockSupport.releaseRoomMutationLock(roomCodeValue, roomMutationLockToken);
            }
            if (submissionLocked) {
                flipbookSubmissionLockSupport.releaseSubmissionLock(roomCodeValue, request.flipbookIndex(),
                    request.frameIndex(), round, viewerUserUuid, submissionLockToken);
            }
        }
    }
}
