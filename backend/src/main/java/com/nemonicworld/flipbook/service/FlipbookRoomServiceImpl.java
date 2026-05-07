package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
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
    private final FlipbookRoomKickUseCase flipbookRoomKickUseCase;
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
    public FlipbookRoomKickResponse kickParticipant(String userUuidValue, String roomCodeValue,
        String targetUserUuidValue) {
        return flipbookRoomKickUseCase.kickParticipant(userUuidValue, roomCodeValue, targetUserUuidValue);
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
