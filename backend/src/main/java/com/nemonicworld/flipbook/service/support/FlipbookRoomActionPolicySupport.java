package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FlipbookRoomActionPolicySupport {

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
    private static final String ROOM_CLOSED_MESSAGE = "이미 종료된 방입니다.";
    private static final String CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE = "현재 배정된 프레임이 없습니다.";
    private static final String SELF_KICK_NOT_ALLOWED_MESSAGE = "자기 자신은 강퇴할 수 없습니다.";
    private static final String HOST_KICK_NOT_ALLOWED_MESSAGE = "방장은 강퇴할 수 없습니다.";

    public void validateRoomHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_ALLOWED_MESSAGE);
    }

    public void validateRoomCloseHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_CLOSE_ALLOWED_MESSAGE);
    }

    public void validateKickHost(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant participant) {
        if (participant.host() || roomState.hostUserUuid().equals(viewerUserUuid)) {
            return;
        }

        throw new ForbiddenException(ONLY_HOST_KICK_ALLOWED_MESSAGE);
    }

    public void validateWaitingRoomForSettings(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_SETTINGS_ONLY_MESSAGE);
        }
    }

    public void validateWaitingRoomForKick(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_KICK_ONLY_MESSAGE);
        }
    }

    public void validateWaitingRoomForLeave(FlipbookRoomState roomState) {
        if (roomState.status() != FlipbookRoomStatus.WAITING) {
            throw new ConflictException(WAITING_ROOM_LEAVE_ONLY_MESSAGE);
        }
    }

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

    public void validateStartableRoomStatus(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            return;
        }

        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            throw new ConflictException(GAME_ALREADY_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public void validateAssignmentQueryableRoom(FlipbookRoomState roomState) {
        if (roomState.status() == FlipbookRoomStatus.PLAYING) {
            return;
        }

        if (roomState.status() == FlipbookRoomStatus.WAITING) {
            throw new ConflictException(GAME_NOT_STARTED_MESSAGE);
        }

        throw new ConflictException(ROOM_CLOSED_MESSAGE);
    }

    public FlipbookFrameAssignment requireCurrentAssignment(FlipbookRoomState roomState, String viewerUserUuid) {
        Integer currentRound = roomState.currentRound();
        if (currentRound == null) {
            throw new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE);
        }

        return roomState.assignments().stream().filter(assignment -> assignment.round() == currentRound)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst()
            .orElseThrow(() -> new ConflictException(CURRENT_ASSIGNMENT_NOT_FOUND_MESSAGE));
    }

    public Optional<FlipbookFrameAssignment> findFrameAssignment(FlipbookRoomState roomState, int flipbookIndex,
        int frameIndex) {
        return roomState.assignments().stream().filter(assignment -> assignment.flipbookIndex() == flipbookIndex)
            .filter(assignment -> assignment.frameIndex() == frameIndex).findFirst();
    }

    public void validateKickTarget(String viewerUserUuid, FlipbookRoomState roomState,
        FlipbookRoomParticipant targetParticipant) {
        if (viewerUserUuid.equals(targetParticipant.userUuid())) {
            throw new ConflictException(SELF_KICK_NOT_ALLOWED_MESSAGE);
        }

        if (targetParticipant.host() || roomState.hostUserUuid().equals(targetParticipant.userUuid())) {
            throw new ConflictException(HOST_KICK_NOT_ALLOWED_MESSAGE);
        }
    }

    boolean canStart(FlipbookRoomState roomState, boolean host) {
        return host && roomState.status() == FlipbookRoomStatus.WAITING
            && roomState.participantCount() >= roomState.minParticipants()
            && roomState.participants().stream().allMatch(FlipbookRoomParticipant::connected);
    }
}
