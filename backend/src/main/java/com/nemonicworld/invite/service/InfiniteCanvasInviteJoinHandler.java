package com.nemonicworld.invite.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasInviteMetadataSyncService;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.user.entity.AppUser;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasInviteJoinHandler implements InviteJoinHandler {

    private static final String BOOTH_TYPE_INFINITE_CANVAS_KEBAB = "infinite-canvas";
    private static final String ROLE_HOST = "host";
    private static final String ROLE_PARTICIPANT = "participant";
    private static final String CANVAS_CLOSED_MESSAGE = "이미 종료된 캔버스입니다.";
    private static final String CANVAS_FULL_MESSAGE = "정원이 가득 찬 캔버스입니다.";
    private static final String CANVAS_UPDATE_CONFLICT_MESSAGE = "동시 입장 요청이 많아 캔버스 입장 상태를 갱신하지 못했습니다. 다시 시도해주세요.";
    private static final String DEFAULT_CANVAS_NAME_SUFFIX = "의 무한 캔버스";
    private static final int CANVAS_UPDATE_MAX_RETRIES = 8;
    private static final List<String> DEFAULT_COLORS = List.of("#2F80ED", "#27AE60", "#EB5757", "#F2994A", "#9B51E0",
        "#00A3A3");

    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService;

    public InfiniteCanvasInviteJoinHandler(InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasInviteMetadataSyncService infiniteCanvasInviteMetadataSyncService) {
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasInviteMetadataSyncService = infiniteCanvasInviteMetadataSyncService;
    }

    @Override
    public boolean supports(String boothType) {
        return infiniteCanvasBoothType().equals(boothType) || BOOTH_TYPE_INFINITE_CANVAS_KEBAB.equals(boothType);
    }

    @Override
    public InviteJoinResponse join(InviteMetadata invite, AppUser user) {
        String userUuid = user.getId().toString();

        for (int attempt = 0; attempt < CANVAS_UPDATE_MAX_RETRIES; attempt++) {
            InfiniteCanvasState state = infiniteCanvasRepository.findByCanvasId(invite.roomId())
                .orElseThrow(() -> new ConflictException(CANVAS_CLOSED_MESSAGE));
            if (!state.isActive()) {
                throw new ConflictException(CANVAS_CLOSED_MESSAGE);
            }

            if (state.hasParticipant(userUuid)) {
                return createResponse(invite, state, userUuid, true);
            }

            if (state.participantCount() >= state.maxParticipants()) {
                throw new ConflictException(CANVAS_FULL_MESSAGE);
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            List<InfiniteCanvasParticipant> participants = new ArrayList<>(state.participants());
            participants.add(createParticipant(user, now));
            InfiniteCanvasState updatedState = copyState(state, participants, now);

            if (infiniteCanvasRepository.saveIfUnchanged(state, updatedState)) {
                infiniteCanvasInviteMetadataSyncService.syncWithCanvasState(updatedState);
                return createResponse(invite, updatedState, userUuid, false);
            }
        }

        throw new ConflictException(CANVAS_UPDATE_CONFLICT_MESSAGE);
    }

    private InviteJoinResponse createResponse(InviteMetadata invite, InfiniteCanvasState state, String userUuid,
        boolean alreadyJoined) {
        String hostNickname = findHostNickname(state);
        String roomName = StringUtils.hasText(invite.roomName())
            ? invite.roomName()
            : hostNickname + DEFAULT_CANVAS_NAME_SUFFIX;
        String role = state.ownerUserUuid().equals(userUuid) ? ROLE_HOST : ROLE_PARTICIPANT;

        return new InviteJoinResponse(infiniteCanvasBoothType(), state.canvasId(), roomName, hostNickname,
            state.participantCount(), state.maxParticipants(), role, alreadyJoined);
    }

    private String infiniteCanvasBoothType() {
        return InfiniteCanvasInviteMetadataSyncService.BOOTH_TYPE_INFINITE_CANVAS;
    }

    private InfiniteCanvasParticipant createParticipant(AppUser user, LocalDateTime now) {
        String userUuid = user.getId().toString();
        String fallbackNickname = "참여자-" + userUuid.replace("-", "").substring(0, 6);
        String nickname = StringUtils.hasText(user.getNickname())
            && !AppUser.ANONYMOUS_NICKNAME.equals(user.getNickname()) ? user.getNickname() : fallbackNickname;

        return new InfiniteCanvasParticipant(userUuid, nickname, defaultColor(userUuid), null, false, now, null, now);
    }

    private String findHostNickname(InfiniteCanvasState state) {
        return state.findParticipant(state.ownerUserUuid()).map(InfiniteCanvasParticipant::nickname).orElse("방장");
    }

    private String defaultColor(String userUuid) {
        int index = Math.floorMod(userUuid.hashCode(), DEFAULT_COLORS.size());
        return DEFAULT_COLORS.get(index);
    }

    private InfiniteCanvasState copyState(InfiniteCanvasState state, List<InfiniteCanvasParticipant> participants,
        LocalDateTime updatedAt) {
        return new InfiniteCanvasState(state.canvasId(), state.inviteCode(), state.status(), state.ownerUserUuid(),
            participants, state.elements(), state.operations(), state.locks(), state.cursors(), state.viewport(),
            state.maxParticipants(), state.revision(), state.createdAt(), updatedAt, state.closedAt());
    }
}
