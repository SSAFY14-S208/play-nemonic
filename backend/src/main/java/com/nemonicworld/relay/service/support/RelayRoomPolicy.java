package com.nemonicworld.relay.service.support;

import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomPolicy {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = RelayRoomTimeLimitSettings.DEFAULT_TIME_LIMIT_SECONDS;
    public static final int MIN_PARTICIPANTS = RelayRoomParticipantLimit.DEFAULT_MIN_PARTICIPANTS;
    public static final int MAX_PARTICIPANTS = RelayRoomParticipantLimit.DEFAULT_MAX_PARTICIPANTS;
    public static final int HOST_JOIN_ORDER = 0;
    public static final int ROOM_UPDATE_MAX_RETRIES = 3;
    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = 10L;
    public static final String ROOM_UPDATE_CONFLICT_MESSAGE = "릴레이 방 상태를 갱신할 수 없습니다.";

    private static final String ROOM_NOT_FOUND_MESSAGE = "존재하지 않는 방입니다.";

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomValidationSupport validationSupport;
    private final RelayRoomParticipantPolicySupport participantPolicySupport;
    private final RelayRoomActionPolicySupport actionPolicySupport;
    private final RelayRoomReconnectPolicySupport reconnectPolicySupport;

    public RelayRoomPolicy(RelayRoomRepository relayRoomRepository, RelayRoomValidationSupport validationSupport,
        RelayRoomParticipantPolicySupport participantPolicySupport, RelayRoomActionPolicySupport actionPolicySupport,
        RelayRoomReconnectPolicySupport reconnectPolicySupport) {
        this.relayRoomRepository = relayRoomRepository;
        this.validationSupport = validationSupport;
        this.participantPolicySupport = participantPolicySupport;
        this.actionPolicySupport = actionPolicySupport;
        this.reconnectPolicySupport = reconnectPolicySupport;
    }

    public RelayRoomState findRoomState(String roomCodeValue) {
        return relayRoomRepository.findByRoomCode(roomCodeValue)
            .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MESSAGE));
    }

    public void validateNicknameRegistered(AppUser appUser) {
        validationSupport.validateNicknameRegistered(appUser);
    }

    public void validateRoomCode(String roomCodeValue) {
        validationSupport.validateRoomCode(roomCodeValue);
    }

    public int resolveTimeLimitSeconds(RelayRoomSettingsRequest request) {
        return validationSupport.resolveTimeLimitSeconds(request);
    }

    public int resolveTimeLimitSeconds(RelayRoomSettingsRequest request, RelayRoomTimeLimitSettings settings) {
        return validationSupport.resolveTimeLimitSeconds(request, settings);
    }

    public void validateWaitingRoomForSettings(RelayRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForSettings(roomState);
    }

    public void validateWaitingRoomForKick(RelayRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForKick(roomState);
    }

    public void validateWaitingRoomForLeave(RelayRoomState roomState) {
        actionPolicySupport.validateWaitingRoomForLeave(roomState);
    }

    public void validateStartableRoomStatus(RelayRoomState roomState) {
        actionPolicySupport.validateStartableRoomStatus(roomState);
    }

    public void validateAssignmentQueryableRoom(RelayRoomState roomState) {
        actionPolicySupport.validateAssignmentQueryableRoom(roomState);
    }

    public RelayRoomAssignment requireCurrentAssignment(RelayRoomState roomState, String viewerUserUuid) {
        return actionPolicySupport.requireCurrentAssignment(roomState, viewerUserUuid);
    }

    public Optional<RelayRoomAssignment> findAssignment(RelayRoomState roomState, int canvasIndex,
        RelayDrawingPart part) {
        return actionPolicySupport.findAssignment(roomState, canvasIndex, part);
    }

    public List<RelayRoomParticipant> findStartParticipants(RelayRoomState roomState) {
        return participantPolicySupport.findStartParticipants(roomState);
    }

    public void validateRoomHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        actionPolicySupport.validateRoomHost(viewerUserUuid, roomState, participant);
    }

    public void validateRoomCloseHost(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant) {
        actionPolicySupport.validateRoomCloseHost(viewerUserUuid, roomState, participant);
    }

    public void validateKickHost(String viewerUserUuid, RelayRoomState roomState, RelayRoomParticipant participant) {
        actionPolicySupport.validateKickHost(viewerUserUuid, roomState, participant);
    }

    public void validateManualClosableRoom(RelayRoomState roomState) {
        actionPolicySupport.validateManualClosableRoom(roomState);
    }

    public Optional<RelayRoomParticipant> findParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return participantPolicySupport.findParticipant(roomState, viewerUserUuid);
    }

    public RelayRoomParticipant requireParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return participantPolicySupport.requireParticipant(roomState, viewerUserUuid);
    }

    public RelayRoomParticipant requireConnectionParticipant(RelayRoomState roomState, String viewerUserUuid) {
        return participantPolicySupport.requireConnectionParticipant(roomState, viewerUserUuid);
    }

    public RelayRoomParticipant requireKickTargetParticipant(RelayRoomState roomState, String targetUserUuid) {
        return participantPolicySupport.requireKickTargetParticipant(roomState, targetUserUuid);
    }

    public void validateKickTarget(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant targetParticipant) {
        actionPolicySupport.validateKickTarget(viewerUserUuid, roomState, targetParticipant);
    }

    public void validateJoinableRoom(RelayRoomState roomState) {
        reconnectPolicySupport.validateJoinableRoom(roomState);
    }

    public void validateNotKicked(RelayRoomState roomState, String userUuid) {
        participantPolicySupport.validateNotKicked(roomState, userUuid);
    }

    public void validateNotDropped(RelayRoomState roomState, String userUuid) {
        participantPolicySupport.validateNotDropped(roomState, userUuid);
    }

    public boolean isKicked(RelayRoomState roomState, String userUuid) {
        return participantPolicySupport.isKicked(roomState, userUuid);
    }

    public boolean isDropped(RelayRoomState roomState, String userUuid) {
        return participantPolicySupport.isDropped(roomState, userUuid);
    }

    public int nextJoinOrder(RelayRoomState roomState) {
        return participantPolicySupport.nextJoinOrder(roomState);
    }

    public boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now) {
        return reconnectPolicySupport.canReconnect(participant, now);
    }

    public boolean canReconnect(RelayRoomParticipant participant, LocalDateTime now, Duration reconnectGracePeriod) {
        return reconnectPolicySupport.canReconnect(participant, now, reconnectGracePeriod);
    }

    public boolean requiresReconnectGrace(RelayRoomState roomState) {
        return reconnectPolicySupport.requiresReconnectGrace(roomState);
    }

    public boolean allowsReconnectWithoutGrace(RelayRoomState roomState) {
        return reconnectPolicySupport.allowsReconnectWithoutGrace(roomState);
    }

    public void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now) {
        reconnectPolicySupport.requireReconnectable(participant, now);
    }

    public void requireReconnectable(RelayRoomParticipant participant, LocalDateTime now,
        Duration reconnectGracePeriod) {
        reconnectPolicySupport.requireReconnectable(participant, now, reconnectGracePeriod);
    }

    public void validateWebSocketConnectableRoom(RelayRoomState roomState) {
        reconnectPolicySupport.validateWebSocketConnectableRoom(roomState);
    }

    public RelayRoomViewerBlockedReason findJoinBlockedReason(RelayRoomState roomState, String viewerUserUuid) {
        return reconnectPolicySupport.findJoinBlockedReason(roomState, viewerUserUuid);
    }
}
