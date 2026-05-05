package com.nemonicworld.relay.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
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

    static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    static final int MIN_PARTICIPANTS = 2;
    static final int MAX_PARTICIPANTS = 6;
    static final int HOST_JOIN_ORDER = 0;
    static final int ROOM_UPDATE_MAX_RETRIES = 3;
    static final String ROOM_UPDATE_CONFLICT_MESSAGE = "릴레이 방 상태를 갱신할 수 없습니다.";

    private static final Set<Integer> ALLOWED_TIME_LIMIT_SECONDS = Set.of(30, 45, 60);
    private static final Duration RECONNECT_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";
    private static final String ROOM_FULL_MESSAGE = "방 정원이 가득 찼습니다.";
    private static final String GAME_IN_PROGRESS_MESSAGE = "게임이 진행 중입니다.";
    private static final String RECONNECT_EXPIRED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ROOM_PARTICIPANT_NOT_FOUND_MESSAGE = "릴레이 방에 참여하지 않은 사용자입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";
    private static final String GAME_ALREADY_STARTED_MESSAGE = "이미 게임이 시작되었습니다.";
    private static final String NOT_ENOUGH_PARTICIPANTS_MESSAGE = "최소 2명이 모여야 시작할 수 있습니다.";
    private static final String PARTICIPANTS_DISCONNECTED_MESSAGE = "모든 참여자가 연결된 상태에서만 시작할 수 있습니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRoomRepository relayRoomRepository;

    public RelayRoomPolicy(RoomCodeGenerator roomCodeGenerator, RelayRoomRepository relayRoomRepository) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRoomRepository = relayRoomRepository;
    }

    RelayRoomState findRoomState(String roomCodeValue) {
        return relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    int resolveTimeLimitSeconds(RelayRoomSettingsRequest request) {
        if (request == null || request.timeLimitSeconds() == null
            || !ALLOWED_TIME_LIMIT_SECONDS.contains(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    void validateWaitingRoomForSettings(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    void validateStartableRoomStatus(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_ALREADY_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    List<RelayRoomParticipant> findStartParticipants(RelayRoomState roomState) {
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

    void validateRoomHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
        }
    }

    Optional<RelayRoomParticipant> findParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return roomState.participants().stream()
            .filter(roomParticipant -> roomParticipant.userUuid().equals(viewerUserUuid)).findFirst();
    }

    RelayRoomParticipant requireParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ForbiddenException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    RelayRoomParticipant requireConnectionParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ConflictException(ROOM_PARTICIPANT_NOT_FOUND_MESSAGE));
    }

    void validateJoinableRoom(RelayRoomState roomState) {
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

    int nextJoinOrder(RelayRoomState roomState) {
        return roomState.participants().stream().map(RelayRoomParticipant::joinOrder).max(Comparator.naturalOrder())
            .orElse(-1) + 1;
    }

    boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        LocalDateTime disconnectedAt = participant.disconnectedAt();

        if (disconnectedAt == null) {
            return false;
        }

        return !disconnectedAt.plus(RECONNECT_GRACE_PERIOD).isBefore(now);
    }

    void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now) {
        if (!canReconnect(participant, now)) {
            throw new ConflictException(RECONNECT_EXPIRED_MESSAGE);
        }
    }

    void validateWebSocketConnectableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING || roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState) {
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
