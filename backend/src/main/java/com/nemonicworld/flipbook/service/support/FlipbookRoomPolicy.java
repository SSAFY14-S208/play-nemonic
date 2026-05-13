package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerBlockedReason;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 유스케이스들이 공유하는 기본 정책입니다.
 */
@Component
public class FlipbookRoomPolicy {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = FlipbookRoomTimeLimitSettings.DEFAULT_TIME_LIMIT_SECONDS;
    public static final int MIN_PARTICIPANTS = FlipbookRoomParticipantLimit.DEFAULT_MIN_PARTICIPANTS;
    public static final int MAX_PARTICIPANTS = FlipbookRoomParticipantLimit.DEFAULT_MAX_PARTICIPANTS;
    public static final int MIN_FRAMES_PER_FLIPBOOK = FlipbookMinFramesPerFlipbookSettings.DEFAULT_MIN_FRAMES_PER_FLIPBOOK;
    public static final int HOST_JOIN_ORDER = 0;
    public static final int ROOM_UPDATE_MAX_RETRIES = 3;
    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = defaultReconnectGraceSeconds();
    public static final String ROOM_UPDATE_CONFLICT_MESSAGE = "동시 설정 변경 요청이 많아 방 설정을 갱신하지 못했습니다. 다시 시도해주세요.";
    public static final String ROOM_CONNECTION_UPDATE_CONFLICT_MESSAGE = "동시 접속 상태 변경 요청이 많아 플립북 방 연결 상태를 "
        + "갱신하지 못했습니다. 다시 시도해주세요.";
    public static final String ROOM_KICK_UPDATE_CONFLICT_MESSAGE = "동시 강퇴 요청이 많아 플립북 방 강퇴 상태를 갱신하지 못했습니다. "
        + "다시 시도해주세요.";
    public static final String ROOM_START_UPDATE_CONFLICT_MESSAGE = "동시 게임 시작 요청이 많아 플립북 방 시작 상태를 갱신하지 못했습니"
        + "다. 다시 시도해주세요.";
    public static final String ROOM_TIMEOUT_UPDATE_CONFLICT_MESSAGE = "동시 타임아웃 처리 요청이 많아 플립북 프레임 자동 제출 상태를 "
        + "갱신하지 못했습니다. 다시 시도해주세요.";

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "플립북 방에 참여하지 않은 사용자입니다.";
    private static final String KICK_TARGET_NOT_FOUND_MESSAGE = "강퇴할 참여자를 찾을 수 없습니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String ONLY_HOST_CLOSE_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String ONLY_HOST_KICK_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String CLOSE_BEFORE_RESULT_MESSAGE = "결과 생성 전에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_PLAYING_MESSAGE = "게임 진행 중에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_FINALIZING_MESSAGE = "결과 생성 중에는 방을 종료할 수 없습니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";
    private static final String WAITING_ROOM_KICK_ONLY_MESSAGE = "대기실에서만 강퇴할 수 있습니다.";
    private static final String WAITING_ROOM_LEAVE_ONLY_MESSAGE = "대기실에서만 퇴장할 수 있습니다.";
    private static final String GAME_ALREADY_STARTED_MESSAGE = "이미 게임이 시작되었습니다.";
    private static final String GAME_NOT_STARTED_MESSAGE = "게임이 아직 시작되지 않았습니다.";
    private static final String NOT_ENOUGH_PARTICIPANTS_MESSAGE = "최소 2명이 모여야 시작할 수 있습니다.";
    private static final String PARTICIPANTS_DISCONNECTED_MESSAGE = "모든 참여자가 웹소켓에 연결되어야 게임을 시작할 수 있습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 프레임이 없습니다.";
    private static final String SELF_KICK_NOT_ALLOWED_MESSAGE = "자기 자신은 강퇴할 수 없습니다.";
    private static final String HOST_KICK_NOT_ALLOWED_MESSAGE = "방장은 강퇴할 수 없습니다.";
    private static final String KICKED_ROOM_REJOIN_FORBIDDEN_MESSAGE = "강퇴된 방에는 다시 입장할 수 없습니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "재접속 가능 시간이 만료되어 게임에 다시 참여할 수 없습니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider;

    public FlipbookRoomPolicy(RoomCodeGenerator roomCodeGenerator, FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRuntimeSettingsProvider = flipbookRuntimeSettingsProvider;
    }

    private static long defaultReconnectGraceSeconds() {
        return FlipbookReconnectGraceSettings.DEFAULT_RECONNECT_GRACE_SECONDS;
    }

    /**
     * 기본 닉네임인 '익명' 상태로는 협동 방을 만들 수 없도록 검증합니다.
     */
    public void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    /**
     * 방코드는 Redis 조회 전에 공통 방코드 생성 규칙과 같은 형식인지 먼저 검증합니다.
     */
    public void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    /**
     * 제한 시간 요청값을 검증합니다.
     */
    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request) {
        return resolveTimeLimitSeconds(request, flipbookRuntimeSettingsProvider.currentRoomTimeLimitSettings());
    }

    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request, FlipbookRoomTimeLimitSettings settings) {
        if (request == null || request.timeLimitSeconds() == null || !settings.allows(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    /**
     * Redis에 저장된 플립북 방 상태를 조회하고, 없으면 공통 404 응답으로 변환합니다.
     */
    public FlipbookRoomState findRoomState(String roomCodeValue) {
        return flipbookRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    /**
     * 현재 방 상태에서 특정 사용자의 참여자 정보를 찾습니다.
     */
    public Optional<FlipbookRoomParticipant> findParticipant(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .findFirst();
    }

    /**
     * 설정 변경처럼 참여자 권한이 필요한 동작에서 현재 사용자의 참여자 정보를 요구합니다.
     */
    public FlipbookRoomParticipant requireParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * WebSocket 연결 대상 참여자를 조회합니다.
     */
    public FlipbookRoomParticipant requireConnectionParticipant(FlipbookRoomState roomState, String userUuid) {
        return findParticipant(roomState, userUuid)
            .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    /**
     * 강퇴 대상 참여자 정보를 필수로 조회합니다.
     */
    public FlipbookRoomParticipant requireKickTargetParticipant(FlipbookRoomState roomState, String targetUserUuid) {
        return findParticipant(roomState, targetUserUuid)
            .orElseThrow(() -> new NotFoundException(KICK_TARGET_NOT_FOUND_MESSAGE));
    }

    /**
     * 방장 전용 동작인지 검증합니다.
     */
    public void validateRoomHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
    }

    /**
     * 수동 종료 요청자가 방장인지 검증합니다.
     */
    public void validateRoomCloseHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_CLOSE_ALLOWED_MESSAGE);
    }

    /**
     * 강퇴 요청자가 방장인지 검증합니다.
     */
    public void validateKickHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_KICK_ALLOWED_MESSAGE);
    }

    /**
     * 설정 변경 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForSettings(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    /**
     * 강퇴 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForKick(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_KICK_ONLY_MESSAGE);
        }
    }

    /**
     * 자발적 퇴장이 가능한 방 상태인지 검증합니다.
     */
    public void validateWaitingRoomForLeave(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_LEAVE_ONLY_MESSAGE);
        }
    }

    /**
     * 수동 종료가 가능한 방 상태인지 검증합니다.
     */
    public void validateManualClosableRoom(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.FINISHED) {
            return;
        }

        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            throw new ConflictException(CLOSE_BEFORE_RESULT_MESSAGE);
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            throw new ConflictException(CLOSE_WHILE_PLAYING_MESSAGE);
        }

        if (roomState.status() == FlipbookRoomStatus.FINALIZING) {
            throw new ConflictException(CLOSE_WHILE_FINALIZING_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 게임 시작 가능한 방 상태인지 검증합니다.
     */
    public void validateStartableRoomStatus(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            throw new ConflictException(GAME_ALREADY_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 내 프레임 배정 조회가 가능한 방 상태인지 검증합니다.
     */
    public void validateAssignmentQueryableRoom(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            return;
        }

        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            throw new ConflictException(GAME_NOT_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    /**
     * 현재 라운드에서 사용자가 맡은 프레임 배정을 조회합니다.
     */
    public FlipbookFrameAssignment requireCurrentAssignment(FlipbookRoomState roomState, String viewerUserUuid) {
        Integer currentRound = roomState.currentRound();
        if (currentRound == null) {
            throw new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE);
        }

        return roomState.assignments().stream().filter(assignment -> assignment.round() == currentRound)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst()
            .orElseThrow(() -> new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));
    }

    /**
     * 특정 플립북과 프레임 번호에 해당하는 배정을 조회합니다.
     */
    public Optional<FlipbookFrameAssignment> findFrameAssignment(FlipbookRoomState roomState, int flipbookIndex,
        int frameIndex) {
        return roomState.assignments().stream().filter(assignment -> assignment.flipbookIndex() == flipbookIndex)
            .filter(assignment -> assignment.frameIndex() == frameIndex).findFirst();
    }

    /**
     * 게임 시작에 참여할 대상자를 입장 순서대로 조회합니다.
     */
    public List<FlipbookRoomParticipant> findStartParticipants(FlipbookRoomState roomState) {
        List<FlipbookRoomParticipant> startParticipants = roomState.participants().stream()
            .sorted(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder)).toList();

        if (startParticipants.stream().anyMatch(participant -> !participant.connected())) {
            throw new ConflictException(PARTICIPANTS_DISCONNECTED_MESSAGE);
        }

        if (startParticipants.size() < roomState.minParticipants()) {
            throw new ConflictException(NOT_ENOUGH_PARTICIPANTS_MESSAGE);
        }

        return startParticipants;
    }

    /**
     * 플립북당 최소 8프레임이 보장되는 기본 라운드 수를 반환합니다.
     */
    public int resolveDefaultTotalRounds() {
        return resolveDefaultTotalRounds(flipbookRuntimeSettingsProvider.currentMinFramesPerFlipbook());
    }

    public int resolveDefaultTotalRounds(int minFramesPerFlipbook) {
        return minFramesPerFlipbook;
    }

    /**
     * 강퇴 대상이 허용되는 참여자인지 검증합니다.
     */
    public void validateKickTarget(String viewerUserUuid, FlipbookRoomState roomState,
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
    public void validateWebSocketConnectableRoom(FlipbookRoomState roomState) {
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
     * 이탈 확정된 UUID가 같은 방에 다시 입장하거나 WebSocket 재연결하는 것을 막습니다.
     */
    public void validateNotDropped(FlipbookRoomState roomState, String userUuid) {
        if (isDropped(roomState, userUuid)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    /**
     * 사용자가 현재 방의 강퇴 목록에 포함되어 있는지 확인합니다.
     */
    public boolean isKicked(FlipbookRoomState roomState, String userUuid) {
        return roomState.kickedUserUuids().contains(userUuid);
    }

    /**
     * 사용자가 현재 방에서 이탈 확정 처리되었는지 확인합니다.
     */
    public boolean isDropped(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream()
            .anyMatch(participant -> participant.userUuid().equals(userUuid) && participant.dropped());
    }

    /**
     * 재접속 유예 시간 검사가 필요한 방 상태인지 판단합니다.
     */
    public boolean requiresReconnectGrace(FlipbookRoomState roomState) {
        return roomState.status() == FlipbookRoomStatus.PLAYING;
    }

    /**
     * 끊겼던 참여자가 아직 재접속 가능한 시간 안에 있는지 계산합니다.
     */
    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now) {
        return canReconnect(participant, now, flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public boolean canReconnect(FlipbookRoomParticipant participant, LocalDateTime now, Duration reconnectGracePeriod) {
        if (participant.dropped()) {
            return false;
        }

        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(reconnectGracePeriod).isBefore(now);
    }

    /**
     * 재접속 가능 시간이 지난 참여자의 invite 재입장과 WebSocket 재연결을 막습니다.
     */
    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now) {
        requireReconnectable(participant, now, flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public void requireReconnectable(FlipbookRoomParticipant participant, LocalDateTime now,
        Duration reconnectGracePeriod) {
        if (!canReconnect(participant, now, reconnectGracePeriod)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    /**
     * 기존 참여자가 방에 다시 들어오려고 할 때 재접속 유예 시간 정책을 검증합니다.
     */
    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now) {
        validateExistingParticipantReturn(roomState, participant, now,
            flipbookRuntimeSettingsProvider.currentReconnectGracePeriod());
    }

    public void validateExistingParticipantReturn(FlipbookRoomState roomState, FlipbookRoomParticipant participant,
        LocalDateTime now, Duration reconnectGracePeriod) {
        if (participant.dropped()) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }

        if (participant.connected()) {
            return;
        }

        if (participant.disconnectedAt() != null && requiresReconnectGrace(roomState)) {
            requireReconnectable(participant, now, reconnectGracePeriod);
        }
    }

    /**
     * 비참여자가 지금 플립북 방에 신규 입장할 수 없는 이유를 계산합니다.
     */
    FlipbookRoomViewerBlockedReason findJoinBlockedReason(FlipbookRoomState roomState, String viewerUserUuid) {
        if (isKicked(roomState, viewerUserUuid)) {
            return FlipbookRoomViewerBlockedReason.KICKED;
        }

        if (isDropped(roomState, viewerUserUuid)) {
            return FlipbookRoomViewerBlockedReason.RECONNECT_EXPIRED;
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

        if (roomState.status() == FlipbookRoomStatus.FINALIZING || roomState.status() == FlipbookRoomStatus.FINISHED) {
            return FlipbookRoomViewerBlockedReason.ROOM_FINISHED;
        }

        return FlipbookRoomViewerBlockedReason.ROOM_CLOSED;
    }

    /**
     * 방장이 대기 중 방을 시작할 수 있는 상태인지 계산합니다.
     */
    boolean canStart(FlipbookRoomState roomState, boolean host) {
        return host && roomState.status() == FlipbookRoomStatus.WAITING
            && roomState.participantCount() >= roomState.minParticipants()
            && roomState.participants().stream().allMatch(FlipbookRoomParticipant::connected);
    }
}
