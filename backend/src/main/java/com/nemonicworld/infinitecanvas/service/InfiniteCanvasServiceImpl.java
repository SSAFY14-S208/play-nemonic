package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasParticipantUpdateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasParticipantLimit;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasRuntimeSettingsProvider;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
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
    private static final String CANVAS_FULL_MESSAGE = "무한 캔버스 최대 참여자 수를 초과했습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String UPDATE_CONFLICT_MESSAGE = "무한 캔버스 상태 갱신 충돌이 발생했습니다. 다시 시도해주세요.";
    private static final int UPDATE_MAX_RETRIES = 8;
    private static final List<String> DEFAULT_COLORS = List.of("#2F80ED", "#27AE60", "#EB5757", "#F2994A", "#9B51E0",
        "#00A3A3");

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InviteRepository inviteRepository;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;
    private final InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider;

    public InfiniteCanvasServiceImpl(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        InviteRepository inviteRepository, InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService,
        InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.inviteRepository = inviteRepository;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
        this.infiniteCanvasRuntimeSettingsProvider = infiniteCanvasRuntimeSettingsProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request) {
        AppUser ownerUser = anonymousUserResolver.resolve(userUuidValue);
        String ownerUserUuid = ownerUser.getId().toString();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String canvasId = UUID.randomUUID().toString();
        String inviteCode = roomCodeGenerator.generateUnique(inviteRepository::existsByInviteCode);
        InfiniteCanvasParticipantLimit participantLimit = infiniteCanvasRuntimeSettingsProvider
            .currentParticipantLimit();
        InfiniteCanvasParticipant ownerParticipant = createParticipant(ownerUser, request, now);
        InfiniteCanvasState state = InfiniteCanvasState.create(canvasId, inviteCode, ownerUserUuid, ownerParticipant,
            request == null ? null : request.viewport(), participantLimit.maxParticipants(), now);

        infiniteCanvasRepository.save(state);
        infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(state);

        return InfiniteCanvasStateResponse.from(state, ownerUserUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasStateResponse getCanvas(String userUuidValue, String canvasId) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        String viewerUserUuid = viewerUser.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            if (state.hasParticipant(viewerUserUuid)) {
                return InfiniteCanvasStateResponse.from(state, viewerUserUuid);
            }

            if (state.participantCount() >= state.maxParticipants()) {
                throw new ConflictException(CANVAS_FULL_MESSAGE);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            List<InfiniteCanvasParticipant> participants = new ArrayList<>(state.participants());
            participants.add(createParticipant(viewerUser, null, now));
            InfiniteCanvasState updatedState = copyState(state, participants, now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return InfiniteCanvasStateResponse.from(updatedState, viewerUserUuid);
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
    public InfiniteCanvasParticipantResponse updateMyParticipant(String userUuidValue, String canvasId,
        InfiniteCanvasParticipantUpdateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedCanvasId = normalizeCanvasId(canvasId);

        for (int attempt = 0; attempt < UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = findActiveState(normalizedCanvasId);
            InfiniteCanvasParticipant participant = state.findParticipant(userUuid)
                .orElseThrow(() -> new NotFoundException(NOT_PARTICIPANT_MESSAGE));
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            InfiniteCanvasParticipant updatedParticipant = participant.updateProfile(
                normalizeNickname(request == null ? null : request.nickname(), participant.nickname()),
                normalizeColor(request == null ? null : request.color(), participant.color()),
                normalizeAvatarUrl(request == null ? null : request.avatarUrl(), participant.avatarUrl()), now);
            InfiniteCanvasState updatedState = copyState(state,
                replaceParticipant(state.participants(), updatedParticipant), now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                return InfiniteCanvasParticipantResponse.from(updatedParticipant);
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

    private InfiniteCanvasParticipant createParticipant(AppUser user, InfiniteCanvasCreateRequest request,
        LocalDateTime now) {
        String userUuid = user.getId().toString();
        String fallbackNickname = resolveFallbackNickname(user, userUuid);

        return new InfiniteCanvasParticipant(userUuid,
            normalizeNickname(request == null ? null : request.nickname(), fallbackNickname),
            normalizeColor(request == null ? null : request.color(), defaultColor(userUuid)),
            normalizeAvatarUrl(request == null ? null : request.avatarUrl(), null), false, now, null, now);
    }

    private String resolveFallbackNickname(AppUser user, String userUuid) {
        if (StringUtils.hasText(user.getNickname()) && !AppUser.ANONYMOUS_NICKNAME.equals(user.getNickname())) {
            return user.getNickname();
        }

        return "참여자-" + userUuid.replace("-", "").substring(0, 6);
    }

    private String normalizeNickname(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private String normalizeColor(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.length() > 32 ? fallback : trimmed;
    }

    private String normalizeAvatarUrl(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
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
}
