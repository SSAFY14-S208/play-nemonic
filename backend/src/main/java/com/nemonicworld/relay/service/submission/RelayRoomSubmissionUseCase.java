package com.nemonicworld.relay.service.submission;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RelayRoomSubmissionUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelaySubmissionFileSupport relaySubmissionFileSupport;
    private final RelaySubmissionLockSupport relaySubmissionLockSupport;
    private final RelaySubmissionAssignmentSupport relaySubmissionAssignmentSupport;
    private final RelaySubmissionEventSupport relaySubmissionEventSupport;

    public RelayRoomSubmissionUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelaySubmissionFileSupport relaySubmissionFileSupport, RelaySubmissionLockSupport relaySubmissionLockSupport,
        RelaySubmissionAssignmentSupport relaySubmissionAssignmentSupport,
        RelaySubmissionEventSupport relaySubmissionEventSupport) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relaySubmissionFileSupport = relaySubmissionFileSupport;
        this.relaySubmissionLockSupport = relaySubmissionLockSupport;
        this.relaySubmissionAssignmentSupport = relaySubmissionAssignmentSupport;
        this.relaySubmissionEventSupport = relaySubmissionEventSupport;
    }

    @Transactional(readOnly = true)
    public RelayRoomSubmissionResponse submitCurrentPart(String userUuidValue, String roomCodeValue,
        RelayRoomSubmissionRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        RelayDrawingPart requestedPart = relaySubmissionAssignmentSupport.parsePart(request);
        Integer requestedCanvasIndex = request == null ? null : request.canvasIndex();
        String submissionLockToken = relaySubmissionLockSupport.createSubmissionLockToken(viewerUserUuid);
        String roomMutationLockToken = relaySubmissionLockSupport.createRoomMutationLockToken("submission",
            viewerUserUuid);
        boolean submissionLocked = false;
        boolean roomMutationLocked = false;

        try {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);

            Optional<RelayRoomAssignment> requestedAssignment = relaySubmissionAssignmentSupport
                .findRequestedAssignment(roomState, viewerUserUuid, requestedCanvasIndex, requestedPart);
            if (relaySubmissionAssignmentSupport.isDuplicateLookupAllowed(roomState)
                && requestedAssignment.isPresent()) {
                RelayRoomAssignment assignment = requestedAssignment.get();
                if (assignment.status() == RelayAssignmentStatus.SUBMITTED) {
                    return relaySubmissionAssignmentSupport.createResponse(roomState, assignment, true, viewerUserUuid,
                        participant.nickname(), RelayPartAdvanceResult.notAdvanced(roomState,
                            relayRoomPartAdvanceService.calculateProgress(roomState, assignment.part())));
                }
                relaySubmissionAssignmentSupport.validateNotAutoSubmitted(assignment);
            }

            relayRoomPolicy.validateAssignmentQueryableRoom(roomState);
            RelayRoomAssignment currentAssignment = relayRoomPolicy.requireCurrentAssignment(roomState, viewerUserUuid);
            relaySubmissionAssignmentSupport.validateAssignmentMatches(currentAssignment, requestedCanvasIndex,
                requestedPart);
            relaySubmissionAssignmentSupport.validateNotAutoSubmitted(currentAssignment);
            relaySubmissionAssignmentSupport.validateDeadline(roomState.partDeadlineAt());
            submissionLocked = relaySubmissionLockSupport.acquireSubmissionLock(roomState.roomCode(), currentAssignment,
                submissionLockToken);
            relaySubmissionFileSupport.validateFile(request.drawingImage());
            MultipartFile hintImage = relaySubmissionFileSupport.resolveHintImage(request, requestedPart);

            String drawingObjectKey = relaySubmissionFileSupport.createObjectKey(roomState.roomCode(),
                currentAssignment.canvasIndex(), currentAssignment.part(), false);
            String hintObjectKey = hintImage == null
                ? null
                : relaySubmissionFileSupport.createObjectKey(roomState.roomCode(), currentAssignment.canvasIndex(),
                    currentAssignment.part(), true);

            relaySubmissionFileSupport.uploadSubmissionImages(drawingObjectKey, request.drawingImage(), hintObjectKey,
                hintImage);

            String cleanupMessage = "릴레이 제출 Redis 갱신 충돌로 임시 업로드 파일이 정리 대상에 남을 수 있습니다.";
            roomMutationLocked = relaySubmissionLockSupport.acquireRoomMutationLock(roomCodeValue,
                roomMutationLockToken);

            for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
                RelayRoomState latestRoomState = relayRoomPolicy.findRoomState(roomCodeValue);
                RelayRoomParticipant latestParticipant = relayRoomPolicy.requireParticipant(latestRoomState,
                    viewerUserUuid);

                Optional<RelayRoomAssignment> latestRequestedAssignment = relaySubmissionAssignmentSupport
                    .findRequestedAssignment(latestRoomState, viewerUserUuid, requestedCanvasIndex, requestedPart);
                if (latestRequestedAssignment.isPresent()) {
                    RelayRoomAssignment assignment = latestRequestedAssignment.get();
                    if (assignment.status() == RelayAssignmentStatus.SUBMITTED) {
                        return relaySubmissionAssignmentSupport.createResponse(latestRoomState, assignment, true,
                            viewerUserUuid, latestParticipant.nickname(),
                            RelayPartAdvanceResult.notAdvanced(latestRoomState,
                                relayRoomPartAdvanceService.calculateProgress(latestRoomState, assignment.part())));
                    }
                    relaySubmissionAssignmentSupport.validateNotAutoSubmitted(assignment);
                }

                relayRoomPolicy.validateAssignmentQueryableRoom(latestRoomState);
                RelayRoomAssignment latestCurrentAssignment = relayRoomPolicy.requireCurrentAssignment(latestRoomState,
                    viewerUserUuid);
                relaySubmissionAssignmentSupport.validateAssignmentMatches(latestCurrentAssignment,
                    requestedCanvasIndex, requestedPart);
                relaySubmissionAssignmentSupport.validateNotAutoSubmitted(latestCurrentAssignment);

                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                RelayRoomAssignment submittedAssignment = relaySubmissionAssignmentSupport
                    .submitAssignment(latestCurrentAssignment, drawingObjectKey, hintObjectKey, now);
                RelayRoomState submittedRoomState = latestRoomState.withAssignments(relaySubmissionAssignmentSupport
                    .replaceAssignment(latestRoomState.assignments(), latestCurrentAssignment, submittedAssignment),
                    now);
                RelayPartAdvanceResult advanceResult = relayRoomPartAdvanceService
                    .advancePartIfCompleted(submittedRoomState, latestCurrentAssignment.part(), now);

                if (relayRoomRepository.saveIfUnchanged(latestRoomState, advanceResult.roomState())) {
                    relayInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                    relaySubmissionEventSupport.logSubmitted(advanceResult.roomState(), submittedAssignment,
                        viewerUserUuid, advanceResult);
                    relaySubmissionEventSupport.logAdvanceEvents(advanceResult, latestCurrentAssignment.part());
                    return relaySubmissionAssignmentSupport.createResponse(advanceResult.roomState(),
                        submittedAssignment, false, viewerUserUuid, latestParticipant.nickname(), advanceResult);
                }

                relaySubmissionEventSupport.logRedisSaveConflict(cleanupMessage, latestRoomState,
                    latestCurrentAssignment, viewerUserUuid, drawingObjectKey);
            }

            relaySubmissionEventSupport.logCasRetryExceeded(roomCodeValue, RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES);
            throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        } finally {
            if (roomMutationLocked) {
                relaySubmissionLockSupport.releaseRoomMutationLock(roomCodeValue, roomMutationLockToken);
            }
            if (submissionLocked) {
                relaySubmissionLockSupport.releaseSubmissionLock(roomCodeValue, requestedCanvasIndex, requestedPart,
                    viewerUserUuid, submissionLockToken);
            }
        }
    }
}
