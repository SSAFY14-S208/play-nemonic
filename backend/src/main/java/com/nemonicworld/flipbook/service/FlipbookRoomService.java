package com.nemonicworld.flipbook.service;

import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCreateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;

/**
 * 플립북 방 유스케이스를 정의합니다.
 */
public interface FlipbookRoomService {

    /**
     * 기존 익명 사용자를 방장으로 하는 새 플립북 방을 생성합니다.
     */
    FlipbookRoomCreateResponse createRoom(String userUuidValue);

    /**
     * 기존 익명 사용자 기준으로 현재 플립북 방 상태를 조회합니다.
     */
    FlipbookRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue);

    /**
     * 방장 기준으로 대기 중인 플립북 방 설정을 변경합니다.
     */
    FlipbookRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        FlipbookRoomSettingsRequest request);

    /**
     * 플립북 WebSocket 연결 성공을 방 참여자 상태에 반영합니다.
     */
    FlipbookRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue);

    /**
     * 플립북 WebSocket 연결 해제를 방 참여자 상태에 반영합니다.
     */
    FlipbookRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue);
}
