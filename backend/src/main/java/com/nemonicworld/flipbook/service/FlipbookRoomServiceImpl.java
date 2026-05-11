package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCloseResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomMyAssignmentResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultsResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.service.close.FlipbookRoomManualCloseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 플립북 방 기능을 유스케이스로 위임하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomServiceImpl implements FlipbookRoomService {

    private final FlipbookRoomCreateUseCase flipbookRoomCreateUseCase;
    private final FlipbookRoomQueryUseCase flipbookRoomQueryUseCase;
    private final FlipbookRoomSettingsUseCase flipbookRoomSettingsUseCase;
    private final FlipbookRoomStartUseCase flipbookRoomStartUseCase;
    private final FlipbookRoomAssignmentQueryUseCase flipbookRoomAssignmentQueryUseCase;
    private final FlipbookFrameSubmitUseCase flipbookFrameSubmitUseCase;
    private final FlipbookRoomResultQueryUseCase flipbookRoomResultQueryUseCase;
    private final FlipbookRoomManualCloseUseCase flipbookRoomManualCloseUseCase;
    private final FlipbookRoomKickUseCase flipbookRoomKickUseCase;
    private final FlipbookRoomLeaveUseCase flipbookRoomLeaveUseCase;
    private final FlipbookRoomConnectionUseCase flipbookRoomConnectionUseCase;

    @Override
    public FlipbookRoomCreateResponse createRoom(String userUuidValue) {
        return flipbookRoomCreateUseCase.createRoom(userUuidValue);
    }

    @Override
    public FlipbookRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        return flipbookRoomQueryUseCase.getRoomState(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        FlipbookRoomSettingsRequest request) {
        return flipbookRoomSettingsUseCase.updateRoomSettings(userUuidValue, roomCodeValue, request);
    }

    @Override
    public FlipbookRoomStateResponse startRoom(String userUuidValue, String roomCodeValue) {
        return flipbookRoomStartUseCase.startRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomMyAssignmentResponse getMyAssignment(String userUuidValue, String roomCodeValue) {
        return flipbookRoomAssignmentQueryUseCase.getMyAssignment(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookFrameSubmitResponse submitFrame(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request) {
        return flipbookFrameSubmitUseCase.submitFrame(userUuidValue, roomCodeValue, round, request);
    }

    @Override
    public FlipbookRoomResultsResponse getResults(String userUuidValue, String roomCodeValue) {
        return flipbookRoomResultQueryUseCase.getResults(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomCloseResponse closeRoom(String userUuidValue, String roomCodeValue) {
        return flipbookRoomManualCloseUseCase.closeRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomKickResponse kickParticipant(String userUuidValue, String roomCodeValue,
        String targetUserUuidValue) {
        return flipbookRoomKickUseCase.kickParticipant(userUuidValue, roomCodeValue, targetUserUuidValue);
    }

    @Override
    public FlipbookRoomLeaveResponse leaveRoom(String userUuidValue, String roomCodeValue) {
        return flipbookRoomLeaveUseCase.leaveRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        return flipbookRoomConnectionUseCase.connectRoom(userUuidValue, roomCodeValue);
    }

    @Override
    public FlipbookRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        return flipbookRoomConnectionUseCase.disconnectRoom(userUuidValue, roomCodeValue);
    }
}
