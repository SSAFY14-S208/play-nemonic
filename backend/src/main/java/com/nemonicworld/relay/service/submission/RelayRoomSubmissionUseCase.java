package com.nemonicworld.relay.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayPartProgress;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Service
public class RelayRoomSubmissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomSubmissionUseCase.class);

    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";
    private static final String HINT_IMAGE_REQUIRED_MESSAGE = "힌트 이미지가 필요합니다.";
    private static final String INVALID_FILE_SIZE_MESSAGE = "파일 크기가 올바르지 않습니다.";
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "지원하지 않는 파일 형식입니다.";

    private static final String SUBMISSION_IN_PROGRESS_MESSAGE = "이미 제출 처리 중입니다.";

    private static final int ROOM_MUTATION_LOCK_ACQUIRE_ATTEMPTS = 5;
    private static final Duration ROOM_MUTATION_LOCK_RETRY_DELAY = Duration.ofMillis(50);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp");

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelaySubmissionStorage relaySubmissionStorage;
    private final RelaySubmissionLockRepository relaySubmissionLockRepository;
    private final RelayRoomMutationLockRepository relayRoomMutationLockRepository;
    private final MinioStorageProperties minioStorageProperties;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final Duration autoSubmitGrace;
    private final Duration submitLockTtl;
    private final Duration roomMutationLockTtl;

    public RelayRoomSubmissionUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService, RelaySubmissionStorage relaySubmissionStorage,
        RelaySubmissionLockRepository relaySubmissionLockRepository,
        RelayRoomMutationLockRepository relayRoomMutationLockRepository, MinioStorageProperties minioStorageProperties,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        @Value("${nemonic.relay.timeout.auto-submit-grace-ms:2000}") long autoSubmitGraceMs,
        @Value("${nemonic.relay.timeout.submit-lock-ttl-ms:10000}") long submitLockTtlMs,
        @Value("${nemonic.relay.room-mutation-lock-ttl-ms:5000}") long roomMutationLockTtlMs) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relaySubmissionStorage = relaySubmissionStorage;
        this.relaySubmissionLockRepository = relaySubmissionLockRepository;
        this.relayRoomMutationLockRepository = relayRoomMutationLockRepository;
        this.minioStorageProperties = minioStorageProperties;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
        this.submitLockTtl = Duration.ofMillis(Math.max(1L, submitLockTtlMs));
        this.roomMutationLockTtl = Duration.ofMillis(Math.max(1L, roomMutationLockTtlMs));
    }

    @Transactional(readOnly = true)
    public RelayRoomSubmissionResponse submitCurrentPart(String userUuidValue, String roomCodeValue,
        RelayRoomSubmissionRequest request) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        RelayDrawingPart requestedPart = parsePart(request);
        Integer requestedCanvasIndex = request == null ? null : request.canvasIndex();
        String submissionLockToken = createSubmissionLockToken(viewerUserUuid);
        String roomMutationLockToken = createRoomMutationLockToken("submission", viewerUserUuid);
        boolean submissionLocked = false;
        boolean roomMutationLocked = false;

        try {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);

            Optional<RelayRoomAssignment> requestedAssignment = findRequestedAssignment(roomState, viewerUserUuid,
                requestedCanvasIndex, requestedPart);
            if (isDuplicateLookupAllowed(roomState) && requestedAssignment.isPresent()) {
                RelayRoomAssignment assignment = requestedAssignment.get();
                if (assignment.status() == RelayAssignmentStatus.SUBMITTED) {
                    return createResponse(roomState, assignment, true, viewerUserUuid, participant.nickname(),
                        RelayPartAdvanceResult.notAdvanced(roomState,
                            relayRoomPartAdvanceService.calculateProgress(roomState, assignment.part())));
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
            submissionLocked = acquireSubmissionLock(roomState.roomCode(), currentAssignment, submissionLockToken);
            validateFile(request.drawingImage());
            MultipartFile hintImage = resolveHintImage(request, requestedPart);

            String drawingObjectKey = createObjectKey(roomState.roomCode(), currentAssignment.canvasIndex(),
                currentAssignment.part(), false);
            String hintObjectKey = hintImage == null
                ? null
                : createObjectKey(roomState.roomCode(), currentAssignment.canvasIndex(), currentAssignment.part(),
                    true);

            uploadSubmissionImages(drawingObjectKey, request.drawingImage(), hintObjectKey, hintImage);

            String cleanupMessage = "릴레이 제출 Redis 갱신 충돌로 임시 업로드 파일이 정리 대상에 남을 수 있습니다.";
            roomMutationLocked = acquireRoomMutationLock(roomCodeValue, roomMutationLockToken);

            for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
                RelayRoomState latestRoomState = relayRoomPolicy.findRoomState(roomCodeValue);
                RelayRoomParticipant latestParticipant = relayRoomPolicy.requireParticipant(latestRoomState,
                    viewerUserUuid);

                Optional<RelayRoomAssignment> latestRequestedAssignment = findRequestedAssignment(latestRoomState,
                    viewerUserUuid, requestedCanvasIndex, requestedPart);
                if (latestRequestedAssignment.isPresent()) {
                    RelayRoomAssignment assignment = latestRequestedAssignment.get();
                    if (assignment.status() == RelayAssignmentStatus.SUBMITTED) {
                        return createResponse(latestRoomState, assignment, true, viewerUserUuid,
                            latestParticipant.nickname(), RelayPartAdvanceResult.notAdvanced(latestRoomState,
                                relayRoomPartAdvanceService.calculateProgress(latestRoomState, assignment.part())));
                    }
                    if (assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()) {
                        throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                    }
                }

                relayRoomPolicy.validateAssignmentQueryableRoom(latestRoomState);
                RelayRoomAssignment latestCurrentAssignment = relayRoomPolicy.requireCurrentAssignment(latestRoomState,
                    viewerUserUuid);
                validateAssignmentMatches(latestCurrentAssignment, requestedCanvasIndex, requestedPart);

                if (latestCurrentAssignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED
                    || latestCurrentAssignment.autoSubmitted()) {
                    throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
                }

                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                RelayRoomAssignment submittedAssignment = submitAssignment(latestCurrentAssignment, drawingObjectKey,
                    hintObjectKey, now);
                RelayRoomState submittedRoomState = latestRoomState.withAssignments(
                    replaceAssignment(latestRoomState.assignments(), latestCurrentAssignment, submittedAssignment),
                    now);
                RelayPartAdvanceResult advanceResult = relayRoomPartAdvanceService
                    .advancePartIfCompleted(submittedRoomState, latestCurrentAssignment.part(), now);

                if (relayRoomRepository.saveIfUnchanged(latestRoomState, advanceResult.roomState())) {
                    relayInviteMetadataSyncService.syncWithRoomState(advanceResult.roomState());
                    logSubmitted(advanceResult.roomState(), submittedAssignment, viewerUserUuid, advanceResult);
                    logAdvanceEvents(advanceResult, latestCurrentAssignment.part());
                    return createResponse(advanceResult.roomState(), submittedAssignment, false, viewerUserUuid,
                        latestParticipant.nickname(), advanceResult);
                }

                RelayRoomEventLogger.apiWarn("relay_minio_upload_redis_save_failed", cleanupMessage,
                    metadata("room_id", latestRoomState.roomCode(), "uuid", viewerUserUuid, "canvas_index",
                        latestCurrentAssignment.canvasIndex(), "part", latestCurrentAssignment.part(), "object_key",
                        drawingObjectKey),
                    null);
                log.warn("{} roomCode={}, canvasIndex={}, part={}", cleanupMessage, latestRoomState.roomCode(),
                    latestCurrentAssignment.canvasIndex(), latestCurrentAssignment.part());
            }

            RelayRoomEventLogger.apiWarn(
                "relay_redis_cas_retry_exceeded", "relay submission exceeded Redis CAS retry count", metadata("room_id",
                    roomCodeValue, "operation", "submission", "attempt_count", RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES),
                null);
            throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        } finally {
            if (roomMutationLocked) {
                relayRoomMutationLockRepository.releaseRoomMutationLock(roomCodeValue, roomMutationLockToken);
            }
            if (submissionLocked) {
                relaySubmissionLockRepository.releaseSubmissionLock(roomCodeValue, requestedCanvasIndex, requestedPart,
                    viewerUserUuid, submissionLockToken);
            }
        }
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
        if (partDeadlineAt == null) {
            return;
        }

        LocalDateTime expiresAt = partDeadlineAt.plus(autoSubmitGrace);
        if (!expiresAt.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictException(SUBMISSION_EXPIRED_MESSAGE);
        }
    }

    private boolean acquireSubmissionLock(String roomCode, RelayRoomAssignment assignment, String submissionLockToken) {
        boolean locked = relaySubmissionLockRepository.acquireSubmissionLock(roomCode, assignment.canvasIndex(),
            assignment.part(), assignment.assignedUserUuid(), submissionLockToken, submitLockTtl);
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

    private void logSubmitted(RelayRoomState roomState, RelayRoomAssignment assignment, String userUuid,
        RelayPartAdvanceResult advanceResult) {
        RelayPartProgress progress = advanceResult.progress();
        RelayRoomEventLogger.apiBusiness("relay_drawing_submitted",
            metadata("room_id", roomState.roomCode(), "uuid", userUuid, "canvas_index", assignment.canvasIndex(),
                "part", assignment.part(), "submitted_at", assignment.submittedAt(), "current_part_completed",
                progress.currentPartCompleted()));
    }

    private void logAdvanceEvents(RelayPartAdvanceResult advanceResult, RelayDrawingPart previousPart) {
        if (!advanceResult.advanced()) {
            return;
        }

        RelayRoomState roomState = advanceResult.roomState();
        if (advanceResult.allPartsCompleted()) {
            RelayRoomEventLogger.apiBusiness("relay_all_parts_completed",
                metadata("room_id", roomState.roomCode(), "participant_count", roomState.participantCount(),
                    "assignment_count", roomState.assignments().size(), "completed_at", roomState.updatedAt()));
            return;
        }

        RelayRoomEventLogger.apiBusiness("relay_part_started",
            metadata("room_id", roomState.roomCode(), "part", advanceResult.nextPart(), "previous_part", previousPart,
                "participant_count", roomState.participantCount(), "part_deadline_at",
                advanceResult.nextPartDeadlineAt()));
    }

    private void sleepBeforeRoomMutationLockRetry() {
        try {
            Thread.sleep(ROOM_MUTATION_LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        }
    }

    private String createRoomMutationLockToken(String owner, String viewerUserUuid) {
        return "token=%s,requestedAt=%s,owner=%s,userUuid=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), owner, viewerUserUuid);
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

    private RelayRoomSubmissionResponse createResponse(RelayRoomState roomState, RelayRoomAssignment assignment,
        boolean alreadySubmitted, String userUuid, String nickname, RelayPartAdvanceResult advanceResult) {
        RelayPartProgress progress = advanceResult.progress();

        return RelayRoomSubmissionResponse.from(roomState.roomCode(), assignment, alreadySubmitted,
            progress.currentPartCompleted(), progress.submittedCount(), progress.totalCount(), userUuid, nickname,
            advanceResult.advanced(), advanceResult.nextPart(), advanceResult.nextPartStartedAt(),
            advanceResult.nextPartDeadlineAt(), advanceResult.allPartsCompleted(), advanceResult.roomState().status());
    }
}
