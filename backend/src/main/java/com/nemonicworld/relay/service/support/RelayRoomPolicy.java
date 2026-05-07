package com.nemonicworld.relay.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 릴레이 방 use case들이 공유하는 검증 정책과 조회 helper입니다.
 */
@Component
public class RelayRoomPolicy {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 6;
    public static final int HOST_JOIN_ORDER = 0;
    public static final int ROOM_UPDATE_MAX_RETRIES = 3;
    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = 10L;
    public static final String ROOM_UPDATE_CONFLICT_MESSAGE = "릴레이 방 상태를 갱신할 수 없습니다.";

    private static final Set<Integer> ALLOWED_TIME_LIMIT_SECONDS = Set.of(30, 45, 60);
    private static final Duration RECONNECT_GRACE_PERIOD = Duration.ofSeconds(DEFAULT_RECONNECT_GRACE_SECONDS);
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String ROOM_FULL_MESSAGE = "방 정원이 가득 찼습니다.";
    private static final String GAME_IN_PROGRESS_MESSAGE = "게임이 진행 중입니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String KICK_TARGET_NOT_FOUND_MESSAGE = "강퇴할 참여자를 찾을 수 없습니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String ONLY_HOST_KICK_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";
    private static final String WAITING_ROOM_KICK_ONLY_MESSAGE = "대기실에서만 강퇴할 수 있습니다.";
    private static final String WAITING_ROOM_LEAVE_ONLY_MESSAGE = "대기실에서만 퇴장할 수 있습니다.";
    private static final String GAME_ALREADY_STARTED_MESSAGE = "이미 게임이 시작되었습니다.";
    private static final String NOT_ENOUGH_PARTICIPANTS_MESSAGE = "최소 2명이 모여야 시작할 수 있습니다.";
    private static final String PARTICIPANTS_DISCONNECTED_MESSAGE = "모든 참여자가 웹소켓에 연결되어야 게임을 시작할 수 있습니다.";
    private static final String GAME_NOT_STARTED_MESSAGE = "게임이 아직 시작되지 않았습니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 그림이 없습니다.";
    private static final String ONLY_HOST_CLOSE_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String CLOSE_BEFORE_RESULT_MESSAGE = "결과 생성 전에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_PLAYING_MESSAGE = "게임 진행 중에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_FINALIZING_MESSAGE = "결과 생성 중에는 방을 종료할 수 없습니다.";
    private static final String SELF_KICK_NOT_ALLOWED_MESSAGE = "자기 자신은 강퇴할 수 없습니다.";
    private static final String HOST_KICK_NOT_ALLOWED_MESSAGE = "방장은 강퇴할 수 없습니다.";
    private static final String KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE = "강퇴된 방에는 다시 입장할 수 없습니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRoomRepository relayRoomRepository;

    public RelayRoomPolicy(RoomCodeGenerator roomCodeGenerator, RelayRoomRepository relayRoomRepository) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRoomRepository = relayRoomRepository;
    }

    /**
     * 방 상태를 조회합니다.
     */
    public RelayRoomState findRoomState(String roomCodeValue) {
        return relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    /**
     * 닉네임 설정 여부를 검증합니다.
     */
    public void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    /**
     * 방 코드 형식을 검증합니다.
     */
    public void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    /**
     * 제한 시간 요청값을 검증합니다.
     */
    public int resolveTimeLimitSeconds(RelayRoomSettingsRequest request) {
        if (request == null || request.timeLimitSeconds() == null
            || !ALLOWED_TIME_LIMIT_SECONDS.contains(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    /**
     * 설정 변경 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForSettings(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    /**
     * 강퇴 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForKick(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_KICK_ONLY_MESSAGE);
        }
    }

    /**
     * 자발적 퇴장이 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForLeave(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.CLOSED) {
            throw new ConflictException(ROOM_CLOSED_MESSAGE);
        }

        throw new ConflictException(WAITING_ROOM_LEAVE_ONLY_MESSAGE);
    }

    /**
     * 게임 시작 가능한 방 상태인지 검증합니다.
     */
    public void validateStartableRoomStatus(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_ALREADY_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 내 배정 조회가 가능한 방 상태인지 검증합니다.
     */
    public void validateAssignmentQueryableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.WAITING) {
            throw new ConflictException(GAME_NOT_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 현재 파트에서 사용자가 맡은 배정을 조회합니다.
     */
    public RelayRoomAssignment requireCurrentAssignment(RelayRoomState roomState, String viewerUserUuid) {
        RelayDrawingPart currentPart = roomState.currentPart();

        return roomState.assignments().stream().filter(assignment -> assignment.part() == currentPart)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst()
            .orElseThrow(() -> new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));
    }

    /**
     * 특정 캔버스와 파트에 해당하는 배정을 조회합니다.
     */
    public Optional<RelayRoomAssignment> findAssignment(RelayRoomState roomState, int canvasIndex,
        RelayDrawingPart part) {
        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == canvasIndex)
            .filter(assignment -> assignment.part() == part).findFirst();
    }

    /**
     * 게임 시작 대상 참여자를 조회합니다.
     */
    public List<RelayRoomParticipant> findStartParticipants(RelayRoomState roomState) {
        List<RelayRoomParticipant> startParticipants = roomState.participants().stream()
            .sorted(Comparator.comparingInt(RelayRoomParticipant::joinOrder)).toList();

        if (startParticipants.stream().anyMatch(participant -> !participant.connected())) {
            throw new ConflictException(PARTICIPANTS_DISCONNECTED_MESSAGE);
        }

        if (startParticipants.size() < roomState.minParticipants()) {
            throw new ConflictException(NOT_ENOUGH_PARTICIPANTS_MESSAGE);
        }

        if (startParticipants.size() > roomState.maxParticipants()) {
            throw new ConflictException(ROOM_FULL_MESSAGE);
        }

        return startParticipants;
    }

    /**
     * 요청자가 방장인지 검증합니다.
     */
    public void validateRoomHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
        }
    }

    /**
     * 수동 종료 요청자가 방장인지 검증합니다.
     */
    public void validateRoomCloseHost(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_CLOSE_ALLOWED_MESSAGE);
        }
    }

    /**
     * 강퇴 요청자가 방장인지 검증합니다.
     */
    public void validateKickHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_KICK_ALLOWED_MESSAGE);
        }
    }

    /**
     * 수동 종료가 가능한 방 상태인지 검증합니다.
     */
    public void validateManualClosableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.FINISHED) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.WAITING) {
            throw new ConflictException(CLOSE_BEFORE_RESULT_MESSAGE);
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(CLOSE_WHILE_PLAYING_MESSAGE);
        }

        if (roomState.status() == RelayRoomStatus.FINALIZING) {
            throw new ConflictException(CLOSE_WHILE_FINALIZING_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 요청자 참여자 정보를 찾습니다.
     */
    public Optional<RelayRoomParticipant> findParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return roomState.participants().stream()
            .filter(roomParticipant -> roomParticipant.userUuid().equals(viewerUserUuid)).findFirst();
    }

    /**
     * 요청자 참여자 정보를 필수로 조회합니다.
     */
    public RelayRoomParticipant requireParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * WebSocket 연결 대상 참여자를 필수로 조회합니다.
     */
    public RelayRoomParticipant requireConnectionParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * 강퇴 대상 참여자 정보를 필수로 조회합니다.
     */
    public RelayRoomParticipant requireKickTargetParticipant(RelayRoomState roomState, String targetUserUuid) {
        return findParticipant(roomState, targetUserUuid)
            .orElseThrow(() -> new NotFoundException(KICK_TARGET_NOT_FOUND_MESSAGE));
    }

    /**
     * 강퇴 대상이 허용되는 참여자인지 검증합니다.
     */
    public void validateKickTarget(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant targetParticipant) {
        if (viewerUserUuid.equals(targetParticipant.userUuid())) {
            throw new ConflictException(SELF_KICK_NOT_ALLOWED_MESSAGE);
        }

        if (targetParticipant.host() || roomState.hostUserUuid().equals(targetParticipant.userUuid())) {
            throw new ConflictException(HOST_KICK_NOT_ALLOWED_MESSAGE);
        }
    }

    /**
     * 입장 가능한 방 상태인지 검증합니다.
     */
    public void validateJoinableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            if (roomState.participantCount() >= roomState.maxParticipants()) {
                throw new ConflictException(ROOM_FULL_MESSAGE);
            }

            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_IN_PROGRESS_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 강퇴된 UUID가 같은 방에 다시 입장하거나 WebSocket 재연결하는 것을 막습니다.
     */
    public void validateNotKicked(RelayRoomState roomState, String userUuid) {
        if (isKicked(roomState, userUuid)) {
            throw new ForbiddenException(KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE);
        }
    }

    /**
     * 이탈 확정된 UUID가 같은 방에 다시 입장하거나 WebSocket 재연결하는 것을 막습니다.
     */
    public void validateNotDropped(RelayRoomState roomState, String userUuid) {
        if (isDropped(roomState, userUuid)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    /**
     * 사용자가 현재 방의 강퇴 목록에 포함되어 있는지 확인합니다.
     */
    public boolean isKicked(RelayRoomState roomState, String userUuid) {
        return roomState.kickedUserUuids().contains(userUuid);
    }

    /**
     * 사용자가 현재 방에서 이탈 확정 처리되었는지 확인합니다.
     */
    public boolean isDropped(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream()
            .anyMatch(participant -> participant.userUuid().equals(userUuid) && participant.dropped());
    }

    /**
     * 다음 입장 순서를 계산합니다.
     */
    public int nextJoinOrder(RelayRoomState roomState) {
        return roomState.participants().stream().map(RelayRoomParticipant::joinOrder).max(Comparator.naturalOrder())
            .orElse(-1) + 1;
    }

    /**
     * 재접속 가능 여부를 계산합니다.
     */
    public boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        if (participant.dropped()) {
            return false;
        }

        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(RECONNECT_GRACE_PERIOD).isBefore(now);
    }

    /**
     * 재접속 유예 시간 검사가 필요한 방 상태인지 판단합니다.
     */
    public boolean requiresReconnectGrace(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.PLAYING;
    }

    /**
     * 재접속 유예 시간 없이 기존 참여자의 재연결을 허용하는 방 상태인지 판단합니다.
     */
    public boolean allowsReconnectWithoutGrace(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.WAITING;
    }

    /**
     * 재접속 가능 상태인지 검증합니다.
     */
    public void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now) {
        if (!canReconnect(participant, now)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    /**
     * WebSocket 연결 가능한 방 상태인지 검증합니다.
     */
    public void validateWebSocketConnectableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING || roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 비참여자 입장 차단 사유를 계산합니다.
     */
    public RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState, String viewerUserUuid) {
        if (isKicked(roomState, viewerUserUuid)) {
            return RelayRoomViewerBlockedReason.KICKED;
        }

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
