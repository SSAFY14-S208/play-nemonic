package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 유스케이스들이 공유하는 기본 정책입니다.
 */
@Component
public class FlipbookRoomPolicy {

    static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    static final int MIN_PARTICIPANTS = 2;
    static final int MAX_PARTICIPANTS = 6;
    static final int HOST_JOIN_ORDER = 0;
    static final int ROOM_UPDATE_MAX_RETRIES = 3;
    static final String ROOM_UPDATE_CONFLICT_MESSAGE = "동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요.";
    static final String ROOM_CONNECTION_UPDATE_CONFLICT_MESSAGE = "동시 접속 상태 변경 요청이 많아 플립북 방 연결 상태를 "
        + "갱신하지 못했습니다. 다시 시도해주세요.";
    static final String ROOM_KICK_UPDATE_CONFLICT_MESSAGE = "동시 강퇴 요청이 많아 플립북 방 강퇴 상태를 갱신하지 못했습니다. 다시 시도해주세요.";

    private static final Set<Integer> ALLOWED_TIME_LIMIT_SECONDS = Set.of(30, 45, 60);
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "플립북 방에 참여하지 않은 사용자입니다.";
    private static final String KICK_TARGET_NOT_FOUND_MESSAGE = "강퇴할 참여자를 찾을 수 없습니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String ONLY_HOST_KICK_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";
    private static final String WAITING_ROOM_KICK_ONLY_MESSAGE = "대기실에서만 강퇴할 수 있습니다.";
    private static final String WAITING_ROOM_LEAVE_ONLY_MESSAGE = "대기실에서만 퇴장할 수 있습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String SELF_KICK_NOT_ALLOWED_MESSAGE = "자기 자신은 강퇴할 수 없습니다.";
    private static final String HOST_KICK_NOT_ALLOWED_MESSAGE = "방장은 강퇴할 수 없습니다.";
    private static final String KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE = "강퇴된 방에는 다시 입장할 수 없습니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRoomRepository flipbookRoomRepository;

    public FlipbookRoomPolicy(RoomCodeGenerator roomCodeGenerator, FlipbookRoomRepository flipbookRoomRepository) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRoomRepository = flipbookRoomRepository;
    }

    /**
     * 기본 닉네임인 '익명' 상태로는 협동 방을 만들 수 없도록 검증합니다.
     */
    void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    /**
     * 방코드는 Redis 조회 전에 공통 방코드 생성 규칙과 같은 형식인지 먼저 검증합니다.
     */
    void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    /**
     * 제한 시간 요청값을 검증합니다.
     */
    int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request) {
        if (request == null || request.timeLimitSeconds() == null
            || !ALLOWED_TIME_LIMIT_SECONDS.contains(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    /**
     * Redis에 저장된 플립북 방 상태를 조회하고, 없으면 공통 404 응답으로 변환합니다.
     */
    FlipbookRoomState findRoomState(String roomCodeValue) {
        return flipbookRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    /**
     * 현재 방 상태에서 특정 사용자의 참여자 정보를 찾습니다.
     */
    Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    /**
     * 설정 변경처럼 참여자 권한이 필요한 동작에서 현재 사용자의 참여자 정보를 요구합니다.
     */
    FlipbookRoomParticipant requireParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * WebSocket 연결 대상 참여자를 조회합니다.
     */
    FlipbookRoomParticipant requireConnectionParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * 강퇴 대상 참여자 정보를 필수로 조회합니다.
     */
    FlipbookRoomParticipant requireKickTargetParticipant(FlipbookRoomState roomState, String targetUserUuid) {
        return findParticipant(roomState, targetUserUuid)
            .orElseThrow(() -> new NotFoundException(KICK_TARGET_NOT_FOUND_MESSAGE));
    }

    /**
     * 방장 전용 동작인지 검증합니다.
     */
    void validateRoomHost(String viewerUserUuid, FlipbookRoomState roomState, FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
    }

    /**
     * 강퇴 요청자가 방장인지 검증합니다.
     */
    void validateKickHost(String viewerUserUuid, FlipbookRoomState roomState, FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_KICK_ALLOWED_MESSAGE);
    }

    /**
     * 설정 변경 가능한 방 상태인지 검증합니다.
     */
    void validateWaitingRoomForSettings(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    /**
     * 강퇴 가능한 방 상태인지 검증합니다.
     */
    void validateWaitingRoomForKick(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_KICK_ONLY_MESSAGE);
        }
    }

    /**
     * 자발적 퇴장이 가능한 방 상태인지 검증합니다.
     */
    void validateWaitingRoomForLeave(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_LEAVE_ONLY_MESSAGE);
        }
    }

    /**
     * 강퇴 대상이 허용되는 참여자인지 검증합니다.
     */
    void validateKickTarget(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant targetParticipant) {
        if (viewerUserUuid.equals(targetParticipant.userUuid())) {
            throw new ConflictException(SELF_KICK_NOT_ALLOWED_MESSAGE);
        }

        if (targetParticipant.host() || roomState.hostUserUuid().equals(targetParticipant.userUuid())) {
            throw new ConflictException(HOST_KICK_NOT_ALLOWED_MESSAGE);
        }
    }

    /**
     * 대기방과 플레이 중 방에서만 WebSocket 연결 상태를 관리합니다.
     */
    void validateWebSocketConnectableRoom(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.WAITING || roomState.status() == FlipbookRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 강퇴된 UUID가 같은 방에 다시 입장하거나 WebSocket 재연결하는 것을 막습니다.
     */
    public void validateNotKicked(FlipbookRoomState roomState, String userUuid) {
        if (isKicked(roomState, userUuid)) {
            throw new ForbiddenException(KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE);
        }
    }

    /**
     * 사용자가 현재 방의 강퇴 목록에 포함되어 있는지 확인합니다.
     */
    public boolean isKicked(FlipbookRoomState roomState, String userUuid) {
        return roomState.kickedUserUuids().contains(userUuid);
    }

    /**
     * 비참여자가 지금 플립북 방에 신규 입장할 수 없는 이유를 계산합니다.
     */
    FlipbookRoomViewerBlockedReason findJoinBlockedReason(FlipbookRoomState roomState, String viewerUserUuid) {
        if (isKicked(roomState, viewerUserUuid)) {
            return FlipbookRoomViewerBlockedReason.KICKED;
        }

        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                return FlipbookRoomViewerBlockedReason.ROOM_FULL;
            }

            return null;
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            return FlipbookRoomViewerBlockedReason.GAME_IN_PROGRESS;
        }

        if (roomState.status() == FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return FlipbookRoomViewerBlockedReason.ROOM_CLOSED;
    }

    /**
     * 방장이 대기 중 방을 시작할 수 있는 상태인지 계산합니다.
     */
    boolean canStart(FlipbookRoomState roomState, boolean host) {
        return host && roomState.status() == FlipbookRoomStatus.WAITING
            && roomState.participantCount() >= roomState.minParticipants();
    }
}
