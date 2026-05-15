package com.nemonicworld.infinitecanvas.service;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasParticipantLimit;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasRuntimeSettingsProvider;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasServiceImpl implements InfiniteCanvasService {

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
}
