package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomCloseResponse;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.dto.response.RelayRoomMyAssignmentResponse;
import com.nemonicworld.relay.dto.response.RelayRoomResultsResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.service.assignment.RelayRoomAssignmentQueryUseCase;
import com.nemonicworld.relay.service.close.RelayRoomManualCloseUseCase;
import com.nemonicworld.relay.service.game.RelayRoomStartUseCase;
import com.nemonicworld.relay.service.room.RelayRoomConnectionUseCase;
import com.nemonicworld.relay.service.room.RelayRoomCreateUseCase;
import com.nemonicworld.relay.service.room.RelayRoomJoinUseCase;
import com.nemonicworld.relay.service.room.RelayRoomKickUseCase;
import com.nemonicworld.relay.service.room.RelayRoomLeaveUseCase;
import com.nemonicworld.relay.service.room.RelayRoomQueryUseCase;
import com.nemonicworld.relay.service.room.RelayRoomSettingsUseCase;
import com.nemonicworld.relay.service.result.RelayRoomResultQueryUseCase;
import com.nemonicworld.relay.service.submission.RelayRoomSubmissionUseCase;
import org.springframework.stereotype.Service;

/**
 * 릴레이 방 기능을 유스케이스로 위임하는 서비스입니다.
 */
@Service
public class RelayRoomServiceImpl implements RelayRoomService {

    private final RelayRoomCreateUseCase relayRoomCreateUseCase;
    private final RelayRoomQueryUseCase relayRoomQueryUseCase;
    private final RelayRoomJoinUseCase relayRoomJoinUseCase;
    private final RelayRoomKickUseCase relayRoomKickUseCase;
    private final RelayRoomLeaveUseCase relayRoomLeaveUseCase;
    private final RelayRoomSettingsUseCase relayRoomSettingsUseCase;
    private final RelayRoomStartUseCase relayRoomStartUseCase;
    private final RelayRoomAssignmentQueryUseCase relayRoomAssignmentQueryUseCase;
    private final RelayRoomResultQueryUseCase relayRoomResultQueryUseCase;
    private final RelayRoomSubmissionUseCase relayRoomSubmissionUseCase;
    private final RelayRoomManualCloseUseCase relayRoomManualCloseUseCase;
    private final RelayRoomConnectionUseCase relayRoomConnectionUseCase;

    public RelayRoomServiceImpl(RelayRoomCreateUseCase relayRoomCreateUseCase,
        RelayRoomQueryUseCase relayRoomQueryUseCase, RelayRoomJoinUseCase relayRoomJoinUseCase,
        RelayRoomKickUseCase relayRoomKickUseCase, RelayRoomLeaveUseCase relayRoomLeaveUseCase,
        RelayRoomSettingsUseCase relayRoomSettingsUseCase, RelayRoomStartUseCase relayRoomStartUseCase,
        RelayRoomAssignmentQueryUseCase relayRoomAssignmentQueryUseCase,
        RelayRoomResultQueryUseCase relayRoomResultQueryUseCase, RelayRoomSubmissionUseCase relayRoomSubmissionUseCase,
        RelayRoomManualCloseUseCase relayRoomManualCloseUseCase,
        RelayRoomConnectionUseCase relayRoomConnectionUseCase) {
        this.relayRoomCreateUseCase = relayRoomCreateUseCase;
        this.relayRoomQueryUseCase = relayRoomQueryUseCase;
        this.relayRoomJoinUseCase = relayRoomJoinUseCase;
        this.relayRoomKickUseCase = relayRoomKickUseCase;
        this.relayRoomLeaveUseCase = relayRoomLeaveUseCase;
        this.relayRoomSettingsUseCase = relayRoomSettingsUseCase;
        this.relayRoomStartUseCase = relayRoomStartUseCase;
        this.relayRoomAssignmentQueryUseCase = relayRoomAssignmentQueryUseCase;
        this.relayRoomResultQueryUseCase = relayRoomResultQueryUseCase;
        this.relayRoomSubmissionUseCase = relayRoomSubmissionUseCase;
        this.relayRoomManualCloseUseCase = relayRoomManualCloseUseCase;
        this.relayRoomConnectionUseCase = relayRoomConnectionUseCase;
    }

    @Override
    public RelayRoomCreateResponse createRoom(String userUuidValue) {
        return relayRoomCreateUseCase.createRoom(userUuidValue);
    }

    @Override
    public RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        return relayRoomQueryUseCase.getRoomState(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomStateResponse joinRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomJoinUseCase.joinRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomKickResponse kickParticipant(String userUuidValue, String roomCodeValue,
        String targetUserUuidValue) {
        return relayRoomKickUseCase.kickParticipant(userUuidValue, roomCodeValue, targetUserUuidValue);
    }

    @Override
    public RelayRoomLeaveResponse leaveRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomLeaveUseCase.leaveRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        RelayRoomSettingsRequest request) {
        return relayRoomSettingsUseCase.updateRoomSettings(userUuidValue, roomCodeValue, request);
    }

    @Override
    public RelayRoomStateResponse startRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomStartUseCase.startRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomMyAssignmentResponse getMyAssignment(String userUuidValue, String roomCodeValue) {
        return relayRoomAssignmentQueryUseCase.getMyAssignment(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomResultsResponse getResults(String userUuidValue, String roomCodeValue) {
        return relayRoomResultQueryUseCase.getResults(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomSubmissionResponse submitCurrentPart(String userUuidValue, String roomCodeValue,
        RelayRoomSubmissionRequest request) {
        return relayRoomSubmissionUseCase.submitCurrentPart(userUuidValue, roomCodeValue, request);
    }

    @Override
    public RelayRoomCloseResponse closeRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomManualCloseUseCase.closeRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomConnectionUseCase.connectRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue, String sessionId) {
        return relayRoomConnectionUseCase.connectRoom(userUuidValue, roomCodeValue, sessionId);
    }

    @Override
    public RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomConnectionUseCase.disconnectRoom(userUuidValue, roomCodeValue);
    }
}
