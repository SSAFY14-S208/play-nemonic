package com.nemonicworld.relay.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/**
 * 릴레이 방 생성과 상태 조회 유스케이스를 처리하는 서비스 구현체입니다.
 *
 * 사용자 검증은 PostgreSQL에서 읽기 전용으로 수행하고, 진행 중 방 상태는 Redis에서만 저장/조회합니다.
 */
public class RelayRoomServiceImpl implements RelayRoomService {

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 60;
    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 6;
    private static final int HOST_JOIN_ORDER = 0;
    private static final Duration RECONNECT_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRoomRepository relayRoomRepository;

    public RelayRoomServiceImpl(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        RelayRoomRepository relayRoomRepository) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRoomRepository = relayRoomRepository;
    }

    /**
     * 방 생성자를 첫 참여자이자 방장으로 포함한 WAITING 상태의 Redis 방을 만듭니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomCreateResponse createRoom(String userUuidValue) {
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);

        validateNicknameRegistered(hostUser);

        String roomCode = roomCodeGenerator.generateUnique(relayRoomRepository::existsByRoomCode);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant hostParticipant = new RelayRoomParticipant(hostUser.getId().toString(),
            hostUser.getNickname(), true, HOST_JOIN_ORDER, true, null, now);
        RelayRoomState roomState = new RelayRoomState(roomCode, RelayRoomStatus.WAITING, hostUser.getId().toString(),
            DEFAULT_TIME_LIMIT_SECONDS, MIN_PARTICIPANTS, MAX_PARTICIPANTS, null, List.of(hostParticipant), now, now);

        relayRoomRepository.save(roomState);

        return RelayRoomCreateResponse.from(roomState);
    }

    /**
     * Redis 방 상태를 변경하지 않고 요청자 기준 viewer 상태만 계산해 응답합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        validateRoomCode(roomCodeValue);

        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
        RelayRoomViewerResponse viewer = createViewerResponse(viewerUser.getId().toString(), roomState,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        return RelayRoomStateResponse.from(roomState, viewer);
    }

    private void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    private void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    private RelayRoomViewerResponse createViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        LocalDateTime now) {
        Optional<RelayRoomParticipant> participant = roomState.participants().stream()
            .filter(roomParticipant -> roomParticipant.userUuid().equals(viewerUserUuid)).findFirst();

        if (participant.isPresent()) {
            return createParticipantViewerResponse(viewerUserUuid, roomState, participant.get(), now);
        }

        return createNonParticipantViewerResponse(viewerUserUuid, roomState);
    }

    private RelayRoomViewerResponse createParticipantViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        boolean host = participant.host() || roomState.hostUserUuid().equals(viewerUserUuid);

        if (participant.connected()) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false, null);
        }

        if (canReconnect(participant, now)) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, true, null);
        }

        return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false,
            RelayRoomViewerBlockedReason.RECONNECT_EXPIRED);
    }

    private boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(RECONNECT_GRACE_PERIOD).isBefore(now);
    }

    private RelayRoomViewerResponse createNonParticipantViewerResponse(String viewerUserUuid,
        RelayRoomState roomState) {
        RelayRoomViewerBlockedReason blockedReason = findJoinBlockedReason(roomState);
        boolean canJoin = blockedReason == null;

        return new RelayRoomViewerResponse(viewerUserUuid, false, false, canJoin, false, blockedReason);
    }

    private RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return RelayRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            return RelayRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == RelayRoomStatus.FINISHED) {
            return RelayRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return RelayRoomViewerBlockedReason.ROOM_CLOSED;
    }
}
