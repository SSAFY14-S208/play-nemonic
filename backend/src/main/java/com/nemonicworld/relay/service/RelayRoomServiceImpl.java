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
    // 재접속 유예 시간은 WebSocket 연결 해제 감지 시각(disconnectedAt)을 기준으로 계산합니다.
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
        // 클라이언트가 보낸 UUID로 새 사용자를 만들지 않고, 이미 발급된 익명 사용자만 허용합니다.
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);

        // 방 생성 시에도 기본 닉네임 사용자를 막아 대기실 표시 이름을 Redis에 안정적으로 복사합니다.
        validateNicknameRegistered(hostUser);

        // 중복 검사는 Redis key 존재 여부로 수행해 공유 링크/실시간 연결/저장 key가 같은 roomCode를 쓰게 합니다.
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
        // 조회 API도 기존 익명 사용자만 사용할 수 있으므로 UUID 검증과 사용자 존재 확인을 먼저 수행합니다.
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        // 방코드 형식이 틀린 요청은 Redis 조회 전에 400으로 차단합니다.
        validateRoomCode(roomCodeValue);

        // Redis miss는 존재하지 않거나 만료된 진행 중 방으로 보고 404 응답으로 변환합니다.
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
        // 현재 API는 스냅샷 조회 전용이므로 Redis participants를 변경하지 않고 viewer 안내값만 계산합니다.
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
        // 요청자가 이미 방에 들어온 참여자인지 먼저 판별한 뒤 참여자/비참여자 정책을 분리합니다.
        Optional<RelayRoomParticipant> participant = roomState.participants().stream()
            .filter(roomParticipant -> roomParticipant.userUuid().equals(viewerUserUuid)).findFirst();

        if (participant.isPresent()) {
            return createParticipantViewerResponse(viewerUserUuid, roomState, participant.get(), now);
        }

        return createNonParticipantViewerResponse(viewerUserUuid, roomState);
    }

    private RelayRoomViewerResponse createParticipantViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        // Redis의 host 플래그와 현재 hostUserUuid 중 하나라도 맞으면 방장으로 판단합니다.
        boolean host = participant.host() || roomState.hostUserUuid().equals(viewerUserUuid);

        // 연결 중인 기존 참여자는 입장/재접속 처리가 필요 없으므로 상태 안내만 반환합니다.
        if (participant.connected()) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false, null);
        }

        // 연결이 끊긴 기존 참여자만 10초 유예 시간 안에서 재접속 가능 여부를 계산합니다.
        if (canReconnect(participant, now)) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, true, null);
        }

        return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false,
            RelayRoomViewerBlockedReason.RECONNECT_EXPIRED);
    }

    private boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        LocalDateTime disconnectedAt = participant.disconnectedAt();

        // disconnectedAt이 없으면 재접속 기준 시각을 알 수 없으므로 만료로 취급합니다.
        if (disconnectedAt == null) {
            return false;
        }

        // 정확히 10초가 지난 경계값까지는 재접속 가능으로 봅니다.
        return !disconnectedAt.plus(RECONNECT_GRACE_PERIOD).isBefore(now);
    }

    private RelayRoomViewerResponse createNonParticipantViewerResponse(String viewerUserUuid,
        RelayRoomState roomState) {
        // 비참여자는 실제 입장 처리 없이 현재 방 상태 기준으로 입장 가능 안내값만 받습니다.
        RelayRoomViewerBlockedReason blockedReason = findJoinBlockedReason(roomState);
        boolean canJoin = blockedReason == null;

        return new RelayRoomViewerResponse(viewerUserUuid, false, false, canJoin, false, blockedReason);
    }

    private RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            // 대기실에서는 정원이 남아 있을 때만 신규 입장이 가능합니다.
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return RelayRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            // 게임이 시작된 뒤에는 신규 UUID 입장을 차단하고, 기존 참여자 재접속만 별도 API에서 처리합니다.
            return RelayRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == RelayRoomStatus.FINISHED) {
            // 결과 생성이 끝난 방은 더 이상 대기실 입장 대상으로 보지 않습니다.
            return RelayRoomViewerBlockedReason.ROOM_FINISHED;
        }

        // 그 밖의 종료 상태는 CLOSED로 안내합니다.
        return RelayRoomViewerBlockedReason.ROOM_CLOSED;
    }
}
