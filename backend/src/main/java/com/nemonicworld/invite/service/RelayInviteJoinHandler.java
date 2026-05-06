package com.nemonicworld.invite.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
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
 * 릴레이 드로잉 초대코드 입장을 담당하는 핸들러입니다.
 */
@Component
@RequiredArgsConstructor
public class RelayInviteJoinHandler implements InviteJoinHandler {

    private static final String BOOTH_TYPE_RELAY = "relay";
    private static final String ROLE_HOST = "host";
    private static final String ROLE_PARTICIPANT = "participant";
    private static final int ROOM_UPDATE_MAX_RETRIES = 3;

    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ROOM_FULL_MESSAGE = "정원이 가득 찬 방입니다.";
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String ROOM_UPDATE_CONFLICT_MESSAGE = "동시 입장 요청이 많아 방 입장 상태를 갱신하지 못했습니다. 다시 시도해주세요.";

    private final RelayRoomRepository relayRoomRepository;

    @Override
    public boolean supports(String boothType) {
        return BOOTH_TYPE_RELAY.equals(boothType);
    }

    /**
     * 릴레이 방 입장을 기존 Redis 낙관적 락 저장 흐름으로 처리합니다.
     */
    @Override
    public InviteJoinResponse join(InviteMetadata invite, AppUser user) {
        String userUuid = user.getId().toString();

        for (int attempt = 0; attempt < ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(invite.roomId())
                .orElseThrow(() -> new ConflictException(ROOM_CLOSED_MESSAGE));
            Optional<RelayRoomParticipant> existingParticipant = findParticipant(roomState, userUuid);

            if (existingParticipant.isPresent()) {
                return createResponse(invite, roomState, userUuid, true);
            }

            validateJoinableRoom(roomState);
            validateNicknameRegistered(user);

            RelayRoomState updatedRoomState = addParticipant(roomState, user);
            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                return createResponse(invite, updatedRoomState, userUuid, false);
            }
        }

        throw new ConflictException(ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    /**
     * 초대 링크 신규 입장은 아직 시작 전인 릴레이 대기방에서만 허용합니다.
     */
    private void validateJoinableRoom(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
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

    private Optional<RelayRoomParticipant> findParticipant(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    /**
     * 기존 Redis room JSON 구조에 맞춰 participants 목록만 추가한 새 방 상태를 만듭니다.
     */
    private RelayRoomState addParticipant(RelayRoomState roomState, AppUser user) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant newParticipant = new RelayRoomParticipant(user.getId().toString(), user.getNickname(),
            false, nextJoinOrder(roomState), true, null, now);

        List<RelayRoomParticipant> participants = new ArrayList<>(roomState.participants());
        participants.add(newParticipant);

        return roomState.withParticipants(participants, now);
    }

    private int nextJoinOrder(RelayRoomState roomState) {
        return roomState.participants().stream().map(RelayRoomParticipant::joinOrder).max(Comparator.naturalOrder())
            .orElse(-1) + 1;
    }

    /**
     * 초대 메타데이터와 갱신된 Redis 방 상태를 조합해 프론트 라우팅 응답을 만듭니다.
     */
    private InviteJoinResponse createResponse(InviteMetadata invite, RelayRoomState roomState, String userUuid,
        boolean alreadyJoined) {
        String hostNickname = findHostNickname(roomState);
        String roomName = StringUtils.hasText(invite.roomName()) ? invite.roomName() : hostNickname + "의 릴레이 드로잉";
        String yourRole = resolveRole(roomState, userUuid);

        return new InviteJoinResponse(normalizeBoothType(invite.boothType()), invite.roomId(), roomName, hostNickname,
            roomState.participantCount(), roomState.maxParticipants(), yourRole, alreadyJoined);
    }

    private String normalizeBoothType(String boothType) {
        return StringUtils.hasText(boothType) ? boothType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String findHostNickname(RelayRoomState roomState) {
        return roomState.participants().stream()
            .filter(participant -> participant.host() || roomState.hostUserUuid().equals(participant.userUuid()))
            .map(RelayRoomParticipant::nickname).findFirst().orElse("방장");
    }

    private String resolveRole(RelayRoomState roomState, String userUuid) {
        boolean host = findParticipant(roomState, userUuid)
            .filter(participant -> participant.host() || roomState.hostUserUuid().equals(userUuid)).isPresent();

        return host ? ROLE_HOST : ROLE_PARTICIPANT;
    }
}
