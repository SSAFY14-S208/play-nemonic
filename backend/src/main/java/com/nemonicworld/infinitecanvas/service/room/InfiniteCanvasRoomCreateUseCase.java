package com.nemonicworld.infinitecanvas.service.room;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCreateResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasRoomCreateUseCase {

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final List<String> DEFAULT_COLORS = List.of("#2F80ED", "#27AE60", "#EB5757", "#F2994A", "#9B51E0",
        "#00A3A3");

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InviteRepository inviteRepository;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;
    private final InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider;

    public InfiniteCanvasRoomCreateUseCase(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InviteRepository inviteRepository,
        InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService,
        InfiniteCanvasRuntimeSettingsProvider infiniteCanvasRuntimeSettingsProvider) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.inviteRepository = inviteRepository;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
        this.infiniteCanvasRuntimeSettingsProvider = infiniteCanvasRuntimeSettingsProvider;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasCreateResponse createCanvas(String userUuidValue, InfiniteCanvasCreateRequest request) {
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);
        validateNicknameRegistered(hostUser);
        String hostUserUuid = hostUser.getId().toString();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String roomCode = roomCodeGenerator.generateUnique(inviteRepository::existsByInviteCode);
        InfiniteCanvasParticipantLimit participantLimit = infiniteCanvasRuntimeSettingsProvider
            .currentParticipantLimit();
        InfiniteCanvasParticipant hostParticipant = createParticipant(hostUser, request, true, now);
        InfiniteCanvasState state = InfiniteCanvasState.create(roomCode, hostUserUuid, hostParticipant, null,
            participantLimit.maxParticipants(), now);

        infiniteCanvasRepository.save(state);
        infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(state);

        return InfiniteCanvasCreateResponse.from(state);
    }

    private InfiniteCanvasParticipant createParticipant(AppUser user, InfiniteCanvasCreateRequest request, boolean host,
        LocalDateTime now) {
        String userUuid = user.getId().toString();

        return new InfiniteCanvasParticipant(userUuid, user.getNickname(),
            normalizeColor(request == null ? null : request.color(), defaultColor(userUuid)), null, host, false, now,
            null, now);
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
}
