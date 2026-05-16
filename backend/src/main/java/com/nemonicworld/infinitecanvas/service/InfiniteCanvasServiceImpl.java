package com.nemonicworld.infinitecanvas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOutputSaveRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOutputSaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasOutputCreateCommand;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasOutputRepository;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasParticipantLimit;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasRuntimeSettingsProvider;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasServiceImpl implements InfiniteCanvasService {

    private static final String INVALID_CANVAS_ID_MESSAGE = "유효하지 않은 캔버스 ID 형식입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_CURSOR_MESSAGE = "커서 좌표 형식이 올바르지 않습니다.";
    private static final String INVALID_LOCK_MESSAGE = "요소 lock 요청 형식이 올바르지 않습니다.";
    private static final String INVALID_ELEMENTS_MESSAGE = "캔버스 요소 목록 형식이 올바르지 않습니다.";
    private static final String INVALID_OPERATIONS_MESSAGE = "캔버스 편집 연산 목록 형식이 올바르지 않습니다.";
    private static final String BASE_REVISION_REQUIRED_MESSAGE = "baseRevision을 지정해주세요.";
    private static final String STALE_REVISION_MESSAGE = "캔버스 revision이 최신이 아닙니다. 서버 상태를 다시 동기화해주세요.";
    private static final String SNAPSHOT_OWNER_MESSAGE = "캔버스 소유자만 전체 스냅샷을 교체할 수 있습니다.";
    private static final String LOCK_CONFLICT_MESSAGE = "다른 참여자가 해당 요소를 편집 중입니다.";
    private static final String LOCK_OWNER_MESSAGE = "요소 lock을 보유한 참여자만 해제할 수 있습니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final String ARTIFACT_KIND_INFINITE_CANVAS = "infinite_canvas";
    private static final String INVALID_IMAGE_FILE_ID_MESSAGE = "유효하지 않은 imageFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String INVALID_INFINITE_CANVAS_FILE_MESSAGE = "무한 캔버스 출력 파일만 저장할 수 있습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String INVALID_META_MESSAGE = "메타데이터 형식이 올바르지 않습니다.";
    private static final String EMPTY_META_JSON = "{}";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int UPDATE_MAX_RETRIES = 8;
    private static final int MAX_ELEMENTS = 5000;
    private static final int MAX_OPERATIONS_PER_MESSAGE = 100;
    private static final int RECENT_OPERATION_LIMIT = 200;
    private static final List<String> DEFAULT_COLORS = List.of("#2F80ED", "#27AE60", "#EB5757", "#F2994A", "#9B51E0",
        "#00A3A3");

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InviteRepository inviteRepository;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasOutputRepository infiniteCanvasOutputRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;
    private final InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider;
    private final FileUploadRepository fileUploadRepository;
    private final ObjectMapper objectMapper;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public InfiniteCanvasServiceImpl(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        InviteRepository inviteRepository, InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasOutputRepository infiniteCanvasOutputRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService,
        InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider,
        FileUploadRepository fileUploadRepository, ObjectMapper objectMapper,
        MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.inviteRepository = inviteRepository;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasOutputRepository = infiniteCanvasOutputRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
        this.infiniteCanvasRuntimeSettingsProvider = infiniteCanvasRuntimeSettingsProvider;
        this.fileUploadRepository = fileUploadRepository;
        this.objectMapper = objectMapper;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request) {
        AppUser ownerUser = anonymousUserResolver.resolve(userUuidValue);
        validateNicknameRegistered(ownerUser);
        String ownerUserUuid = ownerUser.getId().toString();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String canvasId = UUID.randomUUID().toString();
        String inviteCode = roomCodeGenerator.generateUnique(inviteRepository::existsByInviteCode);
        InfiniteCanvasParticipantLimit participantLimit = infiniteCanvasRuntimeSettingsProvider
            .currentParticipantLimit();
        InfiniteCanvasParticipant ownerParticipant = createParticipant(ownerUser, request, now);
        InfiniteCanvasState state = InfiniteCanvasState.create(canvasId, inviteCode, ownerUserUuid, ownerParticipant,
            null, participantLimit.maxParticipants(), now);

        infiniteCanvasRepository.save(state);
        infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(state);

        return InfiniteCanvasStateResponse.from(state, ownerUserUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse acquireLock(String userUuidValue, String canvasId,
        InfiniteCanvasLockRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        String elementId = normalizeElementId(request == null ? null : request.elementId());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            requireParticipant(state, userUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            InfiniteCanvasLock existingLock = locks.get(elementId);
            if (existingLock != null && !userUuid.equals(existingLock.userUuid())) {
                throw new ConflictException(LOCK_CONFLICT_MESSAGE);
            }

            InfiniteCanvasLock lock = new InfiniteCanvasLock(elementId, userUuid, now, now.plus(LOCK_TTL));
            locks.put(elementId, lock);
            InfiniteCanvasState updatedState = copyState(state, state.participants(), state.elements(),
                state.operations(), locks, state.cursors(), state.viewport(), state.revision(), now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasLockResponse(normalizedCanvasId, elementId, lock);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLockResponse releaseLock(String userUuidValue, String canvasId,
        InfiniteCanvasLockRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        String elementId = normalizeElementId(request == null ? null : request.elementId());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            requireParticipant(state, userUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            InfiniteCanvasLock existingLock = locks.get(elementId);
            if (existingLock != null && !userUuid.equals(existingLock.userUuid())) {
                throw new ForbiddenException(LOCK_OWNER_MESSAGE);
            }

            locks.remove(elementId);
            InfiniteCanvasState updatedState = copyState(state, state.participants(), state.elements(),
                state.operations(), locks, state.cursors(), state.viewport(), state.revision(), now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasLockResponse(normalizedCanvasId, elementId, null);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse replaceSnapshot(String userUuidValue, String canvasId,
        InfiniteCanvasSnapshotRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        List<JsonNode> elements = normalizeElements(request == null ? null : request.elements());

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            requireParticipant(state, userUuid);
            requireCanvasOwner(state, userUuid);
            requireFreshRevision(request == null ? null : request.baseRevision(), state.revision());
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            requireNoForeignLocks(locks, userUuid);
            InfiniteCanvasState updatedState = copyState(state, state.participants(), elements, state.operations(),
                locks, state.cursors(), request == null ? state.viewport() : request.viewport(), state.revision() + 1,
                now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasCursorResponse updateCursor(String userUuidValue, String canvasId,
        InfiniteCanvasCursorRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        if (request == null || request.x() == null || request.y() == null || !isFinite(request.x())
            || !isFinite(request.y()) || (request.zoom() != null && !isFinite(request.zoom()))) {
            throw new BadRequestException(INVALID_CURSOR_MESSAGE);
        }

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            requireParticipant(state, userUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasCursor cursor = new InfiniteCanvasCursor(userUuid, request.x(), request.y(), request.zoom(),
                request.payload(), now);
            Map<String, InfiniteCanvasCursor> cursors = new LinkedHashMap<>(state.cursors());
            cursors.put(userUuid, cursor);
            InfiniteCanvasState updatedState = copyState(state, state.participants(), state.elements(),
                state.operations(), removeExpiredLocks(state.locks(), now), cursors, state.viewport(), state.revision(),
                now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasCursorResponse(normalizedCanvasId, cursor);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse connectCanvas(String userUuidValue, String canvasId) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            InfiniteCanvasParticipant participant = state.findParticipant(userUuid)
                .orElseThrow(() -> new NotFoundException(NOT_PARTICIPANT_MESSAGE));

            if (participant.connected()) {
                return InfiniteCanvasStateResponse.from(state, userUuid);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasParticipant connectedParticipant = participant.connect(now);
            InfiniteCanvasState updatedState = copyState(state,
                replaceParticipant(state.participants(), connectedParticipant), now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse disconnectCanvas(String userUuidValue, String canvasId) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            InfiniteCanvasParticipant participant = state.findParticipant(userUuid)
                .orElseThrow(() -> new NotFoundException(NOT_PARTICIPANT_MESSAGE));
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasParticipant disconnectedParticipant = participant.disconnect(now);
            Map<String, InfiniteCanvasLock> locks = removeParticipantLocks(state.locks(), userUuid, now);
            Map<String, InfiniteCanvasCursor> cursors = new LinkedHashMap<>(state.cursors());
            cursors.remove(userUuid);
            InfiniteCanvasState updatedState = copyState(state, state.status(),
                replaceParticipant(state.participants(), disconnectedParticipant), locks, cursors, now,
                state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return InfiniteCanvasStateResponse.from(updatedState, userUuid);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasOpsAppliedResponse applyOperations(String userUuidValue, String canvasId,
        InfiniteCanvasOpsRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        List<InfiniteCanvasOperationRequest> requestedOperations = normalizeOperationRequests(request);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            requireParticipant(state, userUuid);
            List<InfiniteCanvasOperationRequest> pendingOperationRequests = requestedOperations.stream()
                .filter(operationRequest -> !isAlreadyApplied(state, operationRequest, userUuid)).toList();
            if (pendingOperationRequests.isEmpty()) {
                return new InfiniteCanvasOpsAppliedResponse(normalizedCanvasId, state.revision(),
                    state.elements().size(), List.of());
            }
            requireFreshRevision(request == null ? null : request.baseRevision(), state.revision());

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            List<JsonNode> elements = new ArrayList<>(state.elements());
            List<InfiniteCanvasOperation> acceptedOperations = new ArrayList<>();
            Map<String, InfiniteCanvasLock> locks = removeExpiredLocks(state.locks(), now);
            long revision = state.revision();

            for (InfiniteCanvasOperationRequest operationRequest : pendingOperationRequests) {
                validateOperationLock(locks, operationRequest, userUuid);
                revision++;
                InfiniteCanvasOperation operation = createOperation(operationRequest, userUuid, revision, now);
                applyOperation(elements, locks, operation);
                acceptedOperations.add(operation);
            }

            InfiniteCanvasState updatedState = copyState(state, state.participants(), elements,
                appendRecentOperations(state.operations(), acceptedOperations), locks, state.cursors(),
                state.viewport(), revision, now, state.closedAt());

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasOpsAppliedResponse(normalizedCanvasId, revision, elements.size(),
                    acceptedOperations);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasLeaveResponse leaveCanvas(String userUuidValue, String canvasId) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            if (!state.hasParticipant(userUuid)) {
                throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            List<InfiniteCanvasParticipant> participants = state.participants().stream()
                .filter(participant -> !participant.userUuid().equals(userUuid)).toList();
            Map<String, InfiniteCanvasLock> locks = removeParticipantLocks(state.locks(), userUuid, now);
            Map<String, InfiniteCanvasCursor> cursors = new LinkedHashMap<>(state.cursors());
            cursors.remove(userUuid);

            if (participants.isEmpty()) {
                InfiniteCanvasState closedState = copyState(state, InfiniteCanvasStatus.CLOSED, participants, locks,
                    cursors, now, now);
                if (infiniteCanvasRepository.saveIfUnchanged(state, closedState)) {
                    infiniteCanvasRepository.delete(normalizedCanvasId);
                    return new InfiniteCanvasLeaveResponse(normalizedCanvasId, userUuid, true, now);
                }
                continue;
            }

            InfiniteCanvasState updatedState = copyState(state, state.status(), participants, locks, cursors, now,
                state.closedAt());
            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return new InfiniteCanvasLeaveResponse(normalizedCanvasId, userUuid, false, null);
            }
        }

        throw new ConflictException(UPDATE_CONFLICT_MESSAGE);
    }

    @Override
    @Transactional
    public InfiniteCanvasOutputSaveResponse saveOutput(String userUuidValue, String canvasId,
        InfiniteCanvasOutputSaveRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        UUID userUuid = user.getId();
        String normalizedCanvasId = normalizeCanvasId(canvasId);
        InfiniteCanvasState state = findActiveState(normalizedCanvasId);
        requireParticipant(state, userUuid.toString());

        UUID imageFileId = parseRequiredFileId(request == null ? null : request.imageFileId(),
            INVALID_IMAGE_FILE_ID_MESSAGE);
        UUID thumbnailFileId = parseOptionalFileId(request == null ? null : request.thumbnailFileId(),
            INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        String meta = serializeMeta(request == null ? null : request.meta());

        FileUpload imageFileUpload = findFileUpload(imageFileId);
        validateInfiniteCanvasFile(imageFileUpload, userUuid);

        FileUpload thumbnailFileUpload = null;
        if (thumbnailFileId != null) {
            thumbnailFileUpload = findFileUpload(thumbnailFileId);
            validateInfiniteCanvasFile(thumbnailFileUpload, userUuid);
        }

        String imageObjectKey = imageFileUpload.getObjectKey();
        String thumbnailObjectKey = thumbnailFileUpload == null ? imageObjectKey : thumbnailFileUpload.getObjectKey();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        infiniteCanvasOutputRepository.save(new InfiniteCanvasOutputCreateCommand(galleryId, artifactId, userUuid,
            ARTIFACT_KIND_INFINITE_CANVAS, normalizedCanvasId, imageObjectKey, thumbnailObjectKey, meta, now, now));

        return new InfiniteCanvasOutputSaveResponse(galleryId.toString(), artifactId.toString(),
            ARTIFACT_KIND_INFINITE_CANVAS, normalizedCanvasId, minioPublicUrlResolver.resolve(thumbnailObjectKey),
            minioPublicUrlResolver.resolve(imageObjectKey), now);
    }

    private InfiniteCanvasParticipant createParticipant(AppUser user, InfiniteCanvasCreateRequest request,
        LocalDateTime now) {
        String userUuid = user.getId().toString();

        return new InfiniteCanvasParticipant(userUuid, user.getNickname(),
            normalizeColor(request == null ? null : request.color(), defaultColor(userUuid)), null, false, now, null,
            now);
    }

    private void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    private String normalizeColor(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.length() > 32 ? fallback : trimmed;
    }

    private String defaultColor(String userUuid) {
        int index = Math.floorMod(userUuid.hashCode(), DEFAULT_COLORS.size());
        return DEFAULT_COLORS.get(index);
    }

    private InfiniteCanvasState findActiveState(String canvasId) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByCanvasId(canvasId)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
        }
    }

    private void requireCanvasOwner(InfiniteCanvasState state, String userUuid) {
        if (!userUuid.equals(state.ownerUserUuid())) {
            throw new ConflictException(SNAPSHOT_OWNER_MESSAGE);
        }
    }

    private String normalizeCanvasId(String canvasId) {
        if (!StringUtils.hasText(canvasId)) {
            throw new BadRequestException(INVALID_CANVAS_ID_MESSAGE);
        }

        try {
            return UUID.fromString(canvasId.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_CANVAS_ID_MESSAGE);
        }
    }

    private String normalizeElementId(String elementId) {
        if (!StringUtils.hasText(elementId) || elementId.trim().length() > 128) {
            throw new BadRequestException(INVALID_LOCK_MESSAGE);
        }

        return elementId.trim();
    }

    private List<JsonNode> normalizeElements(List<JsonNode> elements) {
        if (elements == null) {
            return List.of();
        }
        if (elements.size() > MAX_ELEMENTS) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }
        if (elements.stream().anyMatch(element -> element == null || element.isNull())) {
            throw new BadRequestException(INVALID_ELEMENTS_MESSAGE);
        }

        return List.copyOf(elements);
    }

    private List<InfiniteCanvasOperationRequest> normalizeOperationRequests(InfiniteCanvasOpsRequest request) {
        List<InfiniteCanvasOperationRequest> operations = request == null ? null : request.operations();
        if (operations == null || operations.isEmpty() || operations.size() > MAX_OPERATIONS_PER_MESSAGE
            || operations.stream().anyMatch(operation -> operation == null || operation.operationType() == null
                || !StringUtils.hasText(operation.clientOperationId()))) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        return List.copyOf(operations);
    }

    private void requireFreshRevision(Long baseRevision, long currentRevision) {
        if (baseRevision == null) {
            throw new BadRequestException(BASE_REVISION_REQUIRED_MESSAGE);
        }
        if (baseRevision.longValue() != currentRevision) {
            throw new ConflictException(STALE_REVISION_MESSAGE);
        }
    }

    private InfiniteCanvasOperation createOperation(InfiniteCanvasOperationRequest request, String userUuid,
        long revision, LocalDateTime now) {
        String operationId = StringUtils.hasText(request.operationId())
            ? request.operationId().trim()
            : UUID.randomUUID().toString();
        JsonNode element = resolveOperationElement(request);
        String elementId = StringUtils.hasText(request.elementId())
            ? request.elementId().trim()
            : extractElementId(element);

        return new InfiniteCanvasOperation(operationId, request.clientOperationId().trim(), request.operationType(),
            elementId, element, request.payload(), userUuid, revision, now);
    }

    private JsonNode resolveOperationElement(InfiniteCanvasOperationRequest request) {
        if (request.element() != null && !request.element().isNull()) {
            return request.element();
        }
        if (request.payload() != null && request.payload().has("element")) {
            return request.payload().get("element");
        }
        return null;
    }

    private boolean isAlreadyApplied(InfiniteCanvasState state, InfiniteCanvasOperationRequest operationRequest,
        String userUuid) {
        if (operationRequest == null || !StringUtils.hasText(operationRequest.clientOperationId())) {
            return false;
        }

        String clientOperationId = operationRequest.clientOperationId().trim();
        return state.operations().stream().anyMatch(operation -> clientOperationId.equals(operation.clientOperationId())
            && userUuid.equals(operation.userUuid()));
    }

    private void validateOperationLock(Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperationRequest operationRequest, String userUuid) {
        InfiniteCanvasOperationType operationType = operationRequest.operationType();
        if (operationType == InfiniteCanvasOperationType.CLEAR_CANVAS) {
            requireNoForeignLocks(locks, userUuid);
            return;
        }

        JsonNode element = resolveOperationElement(operationRequest);
        String elementId = StringUtils.hasText(operationRequest.elementId())
            ? operationRequest.elementId().trim()
            : extractElementId(element);
        if (operationType == InfiniteCanvasOperationType.DELETE_ELEMENT && !StringUtils.hasText(elementId)) {
            throw new BadRequestException(INVALID_OPERATIONS_MESSAGE);
        }

        requireElementEditable(locks, elementId, userUuid);
    }

    private void requireElementEditable(Map<String, InfiniteCanvasLock> locks, String elementId, String userUuid) {
        if (!StringUtils.hasText(elementId)) {
            return;
        }

        InfiniteCanvasLock lock = locks.get(elementId);
        if (lock != null && !userUuid.equals(lock.userUuid())) {
            throw new ConflictException(LOCK_CONFLICT_MESSAGE);
        }
    }

    private void requireNoForeignLocks(Map<String, InfiniteCanvasLock> locks, String userUuid) {
        boolean hasForeignLock = locks.values().stream()
            .anyMatch(lock -> lock != null && !userUuid.equals(lock.userUuid()));
        if (hasForeignLock) {
            throw new ConflictException(LOCK_CONFLICT_MESSAGE);
        }
    }

    private void applyOperation(List<JsonNode> elements, Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperation operation) {
        switch (operation.operationType()) {
            case CLEAR_CANVAS -> {
                elements.clear();
                locks.clear();
            }
            case DELETE_ELEMENT -> {
                removeElement(elements, operation.elementId());
                if (StringUtils.hasText(operation.elementId())) {
                    locks.remove(operation.elementId());
                }
            }
            case CREATE_ELEMENT, UPDATE_ELEMENT, UPSERT_ELEMENT ->
                upsertElement(elements, operation.elementId(), operation.element());
        }
    }

    private void upsertElement(List<JsonNode> elements, String elementId, JsonNode element) {
        if (element == null || element.isNull()) {
            return;
        }

        String resolvedElementId = StringUtils.hasText(elementId) ? elementId : extractElementId(element);
        if (!StringUtils.hasText(resolvedElementId)) {
            elements.add(element);
            return;
        }

        removeElement(elements, resolvedElementId);
        elements.add(element);
    }

    private void removeElement(List<JsonNode> elements, String elementId) {
        if (!StringUtils.hasText(elementId)) {
            return;
        }

        elements.removeIf(element -> elementId.equals(extractElementId(element)));
    }

    private String extractElementId(JsonNode element) {
        if (element == null || !element.isObject()) {
            return null;
        }
        if (StringUtils.hasText(element.path("id").asText(null))) {
            return element.path("id").asText();
        }
        if (StringUtils.hasText(element.path("elementId").asText(null))) {
            return element.path("elementId").asText();
        }
        return null;
    }

    private List<InfiniteCanvasOperation> appendRecentOperations(List<InfiniteCanvasOperation> currentOperations,
        List<InfiniteCanvasOperation> acceptedOperations) {
        List<InfiniteCanvasOperation> operations = new ArrayList<>(currentOperations);
        operations.addAll(acceptedOperations);
        if (operations.size() <= RECENT_OPERATION_LIMIT) {
            return operations;
        }

        return operations.subList(operations.size() - RECENT_OPERATION_LIMIT, operations.size());
    }

    private boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        LocalDateTime updatedAt) {
        return new InfiniteCanvasState(state.canvasId(), state.inviteCode(), state.status(), state.ownerUserUuid(),
            participants, state.elements(), state.operations(), state.locks(), state.cursors(), state.viewport(),
            state.maxParticipants(), state.revision(), state.createdAt(), updatedAt, state.closedAt());
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, InfiniteCanvasStatus status,
        List<InfiniteCanvasParticipant> participants, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, LocalDateTime updatedAt, LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.canvasId(), state.inviteCode(), status, state.ownerUserUuid(),
            participants, state.elements(), state.operations(), locks, cursors, state.viewport(),
            state.maxParticipants(), state.revision(), state.createdAt(), updatedAt, closedAt);
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        List<JsonNode> elements, List<InfiniteCanvasOperation> operations, Map<String, InfiniteCanvasLock> locks,
        Map<String, InfiniteCanvasCursor> cursors, JsonNode viewport, long revision, LocalDateTime updatedAt,
        LocalDateTime closedAt) {
        return new InfiniteCanvasState(state.canvasId(), state.inviteCode(), state.status(), state.ownerUserUuid(),
            participants, elements, operations, locks, cursors, viewport, state.maxParticipants(), revision,
            state.createdAt(), updatedAt, closedAt);
    }

    private List<InfiniteCanvasParticipant> replaceParticipant(List<InfiniteCanvasParticipant> participants,
        InfiniteCanvasParticipant updatedParticipant) {
        return participants.stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();
    }

    private Map<String, InfiniteCanvasLock> removeParticipantLocks(Map<String, InfiniteCanvasLock> locks,
        String userUuid, LocalDateTime now) {
        Map<String, InfiniteCanvasLock> activeLocks = new LinkedHashMap<>();
        locks.forEach((elementId, lock) -> {
            if (lock != null && !lock.isExpired(now) && !userUuid.equals(lock.userUuid())) {
                activeLocks.put(elementId, lock);
            }
        });

        return activeLocks;
    }

    private Map<String, InfiniteCanvasLock> removeExpiredLocks(Map<String, InfiniteCanvasLock> locks,
        LocalDateTime now) {
        Map<String, InfiniteCanvasLock> activeLocks = new LinkedHashMap<>();
        locks.forEach((elementId, lock) -> {
            if (lock != null && !lock.isExpired(now)) {
                activeLocks.put(elementId, lock);
            }
        });

        return activeLocks;
    }

    private UUID parseRequiredFileId(String value, String invalidMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(invalidMessage);
        }

        return parseFileId(value, invalidMessage);
    }

    private UUID parseOptionalFileId(String value, String invalidMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return parseFileId(value, invalidMessage);
    }

    private UUID parseFileId(String value, String invalidMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(invalidMessage);
        }
    }

    private FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    private void validateInfiniteCanvasFile(FileUpload fileUpload, UUID userUuid) {
        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.hasPurpose(FileUploadPurpose.INFINITE_CANVAS)) {
            throw new BadRequestException(INVALID_INFINITE_CANVAS_FILE_MESSAGE);
        }

        if (fileUpload.isDeleted() || !fileUpload.isUploaded()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }
    }

    private String serializeMeta(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return EMPTY_META_JSON;
        }

        if (!meta.isObject()) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }
    }
}
