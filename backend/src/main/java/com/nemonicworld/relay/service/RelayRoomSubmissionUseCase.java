package com.nemonicworld.relay.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.files.config.MinioStorageProperties;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RelayRoomSubmissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomSubmissionUseCase.class);

    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";
    private static final String HINT_IMAGE_REQUIRED_MESSAGE = "힌트 이미지가 필요합니다.";
    private static final String INVALID_FILE_SIZE_MESSAGE = "파일 크기가 올바르지 않습니다.";
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "지원하지 않는 파일 형식입니다.";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp");

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelaySubmissionStorage relaySubmissionStorage;
    private final MinioStorageProperties minioStorageProperties;

    public RelayRoomSubmissionUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelaySubmissionStorage relaySubmissionStorage, MinioStorageProperties minioStorageProperties) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relaySubmissionStorage = relaySubmissionStorage;
        this.minioStorageProperties = minioStorageProperties;
    }

    @Transactional(readOnly = true)
    public RelayRoomSubmissionResponse submitCurrentPart(String userUuidValue, String roomCodeValue,
        RelayRoomSubmissionRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        RelayDrawingPart requestedPart = parsePart(request);
        Integer requestedCanvasIndex = request == null ? null : request.canvasIndex();

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);

            Optional<RelayRoomAssignment> requestedAssignment = findRequestedAssignment(roomState, viewerUserUuid,
                requestedCanvasIndex, requestedPart);
            if (isDuplicateLookupAllowed(roomState) && requestedAssignment.isPresent()) {
                RelayRoomAssignment assignment = requestedAssignment.get();
                if (assignment.status() == RelayAssignmentStatus.SUBMITTED) {
                    return createResponse(roomState, assignment, true, viewerUserUuid, participant.nickname(),
                        PartAdvanceResult.notAdvanced(roomState, calculateProgress(roomState, assignment.part())));
                }
                if (assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()) {
                    throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                }
            }

            relayRoomPolicy.validateAssignmentQueryableRoom(roomState);
            RelayRoomAssignment currentAssignment = relayRoomPolicy.requireCurrentAssignment(roomState, viewerUserUuid);
            validateAssignmentMatches(currentAssignment, requestedCanvasIndex, requestedPart);

            if (currentAssignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED
                || currentAssignment.autoSubmitted()) {
                throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
            }

            validateDeadline(roomState.partDeadlineAt());
            validateFile(request.drawingImage());
            MultipartFile hintImage = resolveHintImage(request, requestedPart);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            String drawingObjectKey = createObjectKey(roomState.roomCode(), currentAssignment.canvasIndex(),
                currentAssignment.part(), false);
            String hintObjectKey = hintImage == null
                ? null
                : createObjectKey(roomState.roomCode(), currentAssignment.canvasIndex(), currentAssignment.part(),
                    true);

            uploadSubmissionImages(drawingObjectKey, request.drawingImage(), hintObjectKey, hintImage);

            RelayRoomAssignment submittedAssignment = submitAssignment(currentAssignment, drawingObjectKey,
                hintObjectKey, now);
            RelayRoomState submittedRoomState = roomState.withAssignments(
                replaceAssignment(roomState.assignments(), currentAssignment, submittedAssignment), now);
            PartAdvanceResult advanceResult = advancePartIfCompleted(submittedRoomState, currentAssignment.part(), now);

            if (relayRoomRepository.saveIfUnchanged(roomState, advanceResult.roomState())) {
                return createResponse(advanceResult.roomState(), submittedAssignment, false, viewerUserUuid,
                    participant.nickname(), advanceResult);
            }

            String cleanupMessage = "릴레이 제출 Redis 갱신 충돌로 임시 업로드 파일이 정리 대상에 남을 수 있습니다.";
            log.warn("{} roomCode={}, canvasIndex={}, part={}", cleanupMessage, roomState.roomCode(),
                currentAssignment.canvasIndex(), currentAssignment.part());
        }

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private RelayDrawingPart parsePart(RelayRoomSubmissionRequest request) {
        if (request == null || !StringUtils.hasText(request.part())) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }

        try {
            return RelayDrawingPart.valueOf(request.part().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    private Optional<RelayRoomAssignment> findRequestedAssignment(RelayRoomState roomState, String viewerUserUuid,
        Integer requestedCanvasIndex, RelayDrawingPart requestedPart) {
        if (requestedCanvasIndex == null) {
            return Optional.empty();
        }

        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == requestedCanvasIndex)
            .filter(assignment -> assignment.part() == requestedPart)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst();
    }

    private boolean isDuplicateLookupAllowed(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.PLAYING || roomState.status() == RelayRoomStatus.FINALIZING;
    }

    private void validateAssignmentMatches(RelayRoomAssignment currentAssignment, Integer requestedCanvasIndex,
        RelayDrawingPart requestedPart) {
        if (requestedCanvasIndex == null || currentAssignment.canvasIndex() != requestedCanvasIndex
            || currentAssignment.part() != requestedPart) {
            throw new ConflictException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    private void validateDeadline(LocalDateTime partDeadlineAt) {
        if (partDeadlineAt != null && partDeadlineAt.isBefore(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictException(SUBMISSION_EXPIRED_MESSAGE);
        }
    }

    private MultipartFile resolveHintImage(RelayRoomSubmissionRequest request, RelayDrawingPart requestedPart) {
        if (requestedPart == RelayDrawingPart.LEGS) {
            return null;
        }

        MultipartFile hintImage = request.hintImage();
        if (hintImage == null || hintImage.isEmpty()) {
            throw new BadRequestException(HINT_IMAGE_REQUIRED_MESSAGE);
        }

        validateFile(hintImage);

        return hintImage;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BadRequestException(INVALID_FILE_SIZE_MESSAGE);
        }

        if (file.getSize() > minioStorageProperties.maxUploadByteSize()) {
            throw new PayloadTooLargeException(createFileSizeExceededMessage());
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException(UNSUPPORTED_FILE_TYPE_MESSAGE);
        }
    }

    private String createFileSizeExceededMessage() {
        long maxSizeMb = minioStorageProperties.maxUploadByteSize() / 1024 / 1024;

        return "파일 크기가 제한을 초과했습니다 (최대 %dMB).".formatted(maxSizeMb);
    }

    private String createObjectKey(String roomCode, int canvasIndex, RelayDrawingPart part, boolean hint) {
        String fileName = part.name().toLowerCase(Locale.ROOT) + (hint ? "-hint.png" : ".png");

        return "relay/tmp/%s/%d/%s".formatted(roomCode, canvasIndex, fileName);
    }

    private void uploadSubmissionImages(String drawingObjectKey, MultipartFile drawingImage, String hintObjectKey,
        MultipartFile hintImage) {
        relaySubmissionStorage.upload(drawingObjectKey, drawingImage);

        if (hintImage != null) {
            relaySubmissionStorage.upload(hintObjectKey, hintImage);
        }
    }

    private RelayRoomAssignment submitAssignment(RelayRoomAssignment currentAssignment, String drawingObjectKey,
        String hintObjectKey, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(currentAssignment.canvasIndex(), currentAssignment.part(),
            currentAssignment.assignedUserUuid(), RelayAssignmentStatus.SUBMITTED, currentAssignment.fileId(),
            drawingObjectKey, hintObjectKey, false, false, submittedAt);
    }

    private List<RelayRoomAssignment> replaceAssignment(List<RelayRoomAssignment> assignments,
        RelayRoomAssignment currentAssignment, RelayRoomAssignment submittedAssignment) {
        List<RelayRoomAssignment> updatedAssignments = new ArrayList<>(assignments.size());
        for (RelayRoomAssignment assignment : assignments) {
            if (assignment.canvasIndex() == currentAssignment.canvasIndex()
                && assignment.part() == currentAssignment.part()
                && assignment.assignedUserUuid().equals(currentAssignment.assignedUserUuid())) {
                updatedAssignments.add(submittedAssignment);
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return updatedAssignments;
    }

    private PartAdvanceResult advancePartIfCompleted(RelayRoomState roomState, RelayDrawingPart submittedPart,
        LocalDateTime now) {
        SubmissionProgress progress = calculateProgress(roomState, submittedPart);
        if (!progress.currentPartCompleted()) {
            return PartAdvanceResult.notAdvanced(roomState, progress);
        }

        if (submittedPart == RelayDrawingPart.FACE) {
            RelayRoomState advancedRoomState = roomState.startPart(RelayDrawingPart.BODY, now);
            return PartAdvanceResult.advanced(advancedRoomState, RelayDrawingPart.BODY, progress);
        }

        if (submittedPart == RelayDrawingPart.BODY) {
            RelayRoomState advancedRoomState = roomState.startPart(RelayDrawingPart.LEGS, now);
            return PartAdvanceResult.advanced(advancedRoomState, RelayDrawingPart.LEGS, progress);
        }

        RelayRoomState finalizedRoomState = roomState.finalizeParts(now);
        return PartAdvanceResult.allPartsCompleted(finalizedRoomState, progress);
    }

    private RelayRoomSubmissionResponse createResponse(RelayRoomState roomState, RelayRoomAssignment assignment,
        boolean alreadySubmitted, String userUuid, String nickname, PartAdvanceResult advanceResult) {
        SubmissionProgress progress = advanceResult.progress();

        return RelayRoomSubmissionResponse.from(roomState.roomCode(), assignment, alreadySubmitted,
            progress.currentPartCompleted(), progress.submittedCount(), progress.totalCount(), userUuid, nickname,
            advanceResult.advanced(), advanceResult.nextPart(), advanceResult.nextPartStartedAt(),
            advanceResult.nextPartDeadlineAt(), advanceResult.allPartsCompleted(), advanceResult.roomState().status());
    }

    private SubmissionProgress calculateProgress(RelayRoomState roomState, RelayDrawingPart part) {
        int totalCount = 0;
        int submittedCount = 0;

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() != part) {
                continue;
            }

            totalCount++;
            if (isCompleted(assignment)) {
                submittedCount++;
            }
        }

        return new SubmissionProgress(submittedCount, totalCount, totalCount > 0 && submittedCount == totalCount);
    }

    private boolean isCompleted(RelayRoomAssignment assignment) {
        return assignment.status() == RelayAssignmentStatus.SUBMITTED
            || assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted();
    }

    private record SubmissionProgress(int submittedCount, int totalCount, boolean currentPartCompleted) {
    }

    private record PartAdvanceResult(RelayRoomState roomState, boolean advanced, RelayDrawingPart nextPart,
        LocalDateTime nextPartStartedAt, LocalDateTime nextPartDeadlineAt, boolean allPartsCompleted,
        SubmissionProgress progress) {

        private static PartAdvanceResult notAdvanced(RelayRoomState roomState, SubmissionProgress progress) {
            return new PartAdvanceResult(roomState, false, null, null, null,
                roomState.status() == RelayRoomStatus.FINALIZING, progress);
        }

        private static PartAdvanceResult advanced(RelayRoomState roomState, RelayDrawingPart nextPart,
            SubmissionProgress progress) {
            return new PartAdvanceResult(roomState, true, nextPart, roomState.partStartedAt(),
                roomState.partDeadlineAt(), false, progress);
        }

        private static PartAdvanceResult allPartsCompleted(RelayRoomState roomState, SubmissionProgress progress) {
            return new PartAdvanceResult(roomState, true, null, null, null, true, progress);
        }
    }
}
