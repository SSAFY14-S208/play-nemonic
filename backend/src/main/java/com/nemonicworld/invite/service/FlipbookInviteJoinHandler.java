package com.nemonicworld.invite.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.user.entity.AppUser;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 플립북 초대코드 입장을 담당하는 핸들러입니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookInviteJoinHandler implements InviteJoinHandler {

    private static final String BOOTH_TYPE_FLIPBOOK = "flipbook";
    private static final String ROLE_HOST = "host";
    private static final String ROLE_PARTICIPANT = "participant";
    private static final int ROOM_UPDATE_MAX_RETRIES = 3;

    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ROOM_FULL_MESSAGE = "정원이 가득 찬 방입니다.";
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String ROOM_UPDATE_CONFLICT_MESSAGE = "방 입장 상태를 갱신할 수 없습니다.";
    private static final String DEFAULT_ROOM_NAME_SUFFIX = "의 플립북";

    private final FlipbookRoomRepository flipbookRoomRepository;

    @Override
    public boolean supports(String boothType) {
        return BOOTH_TYPE_FLIPBOOK.equals(boothType);
    }

    /**
     * 플립북 방 입장을 Redis 낙관적 락 저장 흐름으로 처리합니다.
     */
    @Override
    public InviteJoinResponse join(InviteMetadata invite, AppUser user) {
        String userUuid = user.getId().toString();

        for (int attempt = 0; attempt < ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomRepository.findByRoomCode(invite.roomId())
                .orElseThrow(() -> new ConflictException(ROOM_CLOSED_MESSAGE));
            Optional<FlipbookRoomParticipant> existingParticipant = findParticipant(roomState, userUuid);

            if (existingParticipant.isPresent()) {
                return createResponse(invite, roomState, userUuid, true);
            }

            validateJoinableRoom(roomState);
            validateNicknameRegistered(user);

            FlipbookRoomState updatedRoomState = addParticipant(roomState, user);
            if (flipbookRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                return createResponse(invite, updatedRoomState, userUuid, false);
            }
        }

        throw new IllegalStateException(ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 초대코드 신규 입장은 아직 시작 전인 플립북 대기방에서만 허용합니다.
     */
    private void validateJoinableRoom(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(ROOM_CLOSED_MESSAGE);
        }

        if (roomState.participantCount() >= roomState.maxParticipants()) {
            throw new ConflictException(ROOM_FULL_MESSAGE);
        }
    }

    private void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    private Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    /**
     * 기존 Redis room JSON 구조에 맞춰 participants 목록만 추가한 새 방 상태를 만듭니다.
     */
    private FlipbookRoomState addParticipant(FlipbookRoomState roomState, AppUser user) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant newParticipant = new FlipbookRoomParticipant(user.getId().toString(),
            user.getNickname(), false, nextJoinOrder(roomState), true, null, now);

        List<FlipbookRoomParticipant> participants = new ArrayList<>(roomState.participants());
        participants.add(newParticipant);

        return roomState.withParticipants(participants, now);
    }

    private int nextJoinOrder(FlipbookRoomState roomState) {
        return roomState.participants().stream().map(FlipbookRoomParticipant::joinOrder).max(Comparator.naturalOrder())
            .orElse(-1) + 1;
    }

    /**
     * 초대 메타데이터와 갱신된 Redis 방 상태를 조합해 프론트 라우팅 응답을 만듭니다.
     */
    private InviteJoinResponse createResponse(InviteMetadata invite, FlipbookRoomState roomState, String userUuid,
        boolean alreadyJoined) {
        String hostNickname = findHostNickname(roomState);
        String roomName = StringUtils.hasText(invite.roomName())
            ? invite.roomName()
            : hostNickname + DEFAULT_ROOM_NAME_SUFFIX;
        String yourRole = resolveRole(roomState, userUuid);

        return new InviteJoinResponse(normalizeBoothType(invite.boothType()), invite.roomId(), roomName, hostNickname,
            roomState.participantCount(), roomState.maxParticipants(), yourRole, alreadyJoined);
    }

    private String normalizeBoothType(String boothType) {
        return StringUtils.hasText(boothType) ? boothType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String findHostNickname(FlipbookRoomState roomState) {
        return roomState.participants().stream()
            .filter(participant -> participant.host() || roomState.hostUserUuid().equals(participant.userUuid()))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse("방장");
    }

    private String resolveRole(FlipbookRoomState roomState, String userUuid) {
        boolean host = findParticipant(roomState, userUuid)
            .filter(participant -> participant.host() || roomState.hostUserUuid().equals(userUuid)).isPresent();

        return host ? ROLE_HOST : ROLE_PARTICIPANT;
    }
}
