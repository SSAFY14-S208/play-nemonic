package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomMyAssignmentResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import org.springframework.stereotype.Service;

/**
 * 릴레이 방 기능을 유스케이스로 위임하는 서비스입니다.
 */
@Service
public class RelayRoomServiceImpl implements RelayRoomService {

    private final RelayRoomCreateUseCase relayRoomCreateUseCase;
    private final RelayRoomQueryUseCase relayRoomQueryUseCase;
    private final RelayRoomJoinUseCase relayRoomJoinUseCase;
    private final RelayRoomSettingsUseCase relayRoomSettingsUseCase;
    private final RelayRoomStartUseCase relayRoomStartUseCase;
    private final RelayRoomAssignmentQueryUseCase relayRoomAssignmentQueryUseCase;
    private final RelayRoomConnectionUseCase relayRoomConnectionUseCase;

    public RelayRoomServiceImpl(RelayRoomCreateUseCase relayRoomCreateUseCase,
        RelayRoomQueryUseCase relayRoomQueryUseCase, RelayRoomJoinUseCase relayRoomJoinUseCase,
        RelayRoomSettingsUseCase relayRoomSettingsUseCase, RelayRoomStartUseCase relayRoomStartUseCase,
        RelayRoomAssignmentQueryUseCase relayRoomAssignmentQueryUseCase,
        RelayRoomConnectionUseCase relayRoomConnectionUseCase) {
        this.relayRoomCreateUseCase = relayRoomCreateUseCase;
        this.relayRoomQueryUseCase = relayRoomQueryUseCase;
        this.relayRoomJoinUseCase = relayRoomJoinUseCase;
        this.relayRoomSettingsUseCase = relayRoomSettingsUseCase;
        this.relayRoomStartUseCase = relayRoomStartUseCase;
        this.relayRoomAssignmentQueryUseCase = relayRoomAssignmentQueryUseCase;
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
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomConnectionUseCase.connectRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        return relayRoomConnectionUseCase.disconnectRoom(userUuidValue, roomCodeValue);
    }
}
