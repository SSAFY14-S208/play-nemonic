package com.nemonicworld.relay.service.support;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomActionPolicySupport {

    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String ONLY_HOST_ALLOWED_MESSAGE = "방장만 사용할 수 있습니다.";
    private static final String ONLY_HOST_KICK_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String WAITING_ROOM_SETTINGS_ONLY_MESSAGE = "대기 중인 방에서만 설정을 변경할 수 있습니다.";
    private static final String WAITING_ROOM_KICK_ONLY_MESSAGE = "대기실에서만 강퇴할 수 있습니다.";
    private static final String WAITING_ROOM_LEAVE_ONLY_MESSAGE = "대기실에서만 퇴장할 수 있습니다.";
    private static final String GAME_ALREADY_STARTED_MESSAGE = "이미 게임이 시작되었습니다.";
    private static final String GAME_NOT_STARTED_MESSAGE = "게임이 아직 시작되지 않았습니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 그림이 없습니다.";
    private static final String ONLY_HOST_CLOSE_ALLOWED_MESSAGE = "방장만 사용할 수 있는 기능입니다.";
    private static final String CLOSE_BEFORE_RESULT_MESSAGE = "결과 생성 전에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_PLAYING_MESSAGE = "게임 진행 중에는 방을 종료할 수 없습니다.";
    private static final String CLOSE_WHILE_FINALIZING_MESSAGE = "결과 생성 중에는 방을 종료할 수 없습니다.";
    private static final String SELF_KICK_NOT_ALLOWED_MESSAGE = "자기 자신은 강퇴할 수 없습니다.";
    private static final String HOST_KICK_NOT_ALLOWED_MESSAGE = "방장은 강퇴할 수 없습니다.";

    public void validateWaitingRoomForSettings(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    public void validateWaitingRoomForKick(RelayRoomState roomState) {
        if (roomState.status() != RelayRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_KICK_ONLY_MESSAGE);
        }
    }

    public void validateWaitingRoomForLeave(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.CLOSED) {
            throw new ConflictException(ROOM_CLOSED_MESSAGE);
        }

        throw new ConflictException(WAITING_ROOM_LEAVE_ONLY_MESSAGE);
    }

    public void validateStartableRoomStatus(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.PLAYING) {
            throw new ConflictException(GAME_ALREADY_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public void validateAssignmentQueryableRoom(RelayRoomState roomState) {
        if (roomState.status() == RelayRoomStatus.PLAYING) {
            return;
        }

        if (roomState.status() == RelayRoomStatus.WAITING) {
            throw new ConflictException(GAME_NOT_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public RelayRoomAssignment requireCurrentAssignment(RelayRoomState roomState, String viewerUserUuid) {
        RelayDrawingPart currentPart = roomState.currentPart();

        return roomState.assignments().stream().filter(assignment -> assignment.part() == currentPart)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst()
            .orElseThrow(() -> new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));
    }

    public Optional<RelayRoomAssignment> findAssignment(RelayRoomState roomState, int canvasIndex,
        RelayDrawingPart part) {
        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == canvasIndex)
            .filter(assignment -> assignment.part() == part).findFirst();
    }

    public void validateRoomHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
        }
    }

    public void validateRoomCloseHost(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_CLOSE_ALLOWED_MESSAGE);
        }
    }

    public void validateKickHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        if (!participant.host() && !roomState.hostUserUuid().equals(viewerUserUuid)) {
            throw new ForbiddenException(ONLY_HOST_KICK_ALLOWED_MESSAGE);
        }
    }

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

    public void validateKickTarget(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant targetParticipant) {
        if (viewerUserUuid.equals(targetParticipant.userUuid())) {
            throw new ConflictException(SELF_KICK_NOT_ALLOWED_MESSAGE);
        }

        if (targetParticipant.host() || roomState.hostUserUuid().equals(targetParticipant.userUuid())) {
            throw new ConflictException(HOST_KICK_NOT_ALLOWED_MESSAGE);
        }
    }
}
