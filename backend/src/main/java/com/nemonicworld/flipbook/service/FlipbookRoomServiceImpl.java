package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
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

    @Override
    public FlipbookRoomCreateResponse createRoom(String userUuidValue) {
        return flipbookRoomCreateUseCase.createRoom(userUuidValue);
    }

    @Override
    public FlipbookRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue) {
        return flipbookRoomQueryUseCase.getRoomState(userUuidValue, roomCodeValue);
    }
}
