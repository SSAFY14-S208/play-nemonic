package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;

/**
 * 릴레이 방 유스케이스를 정의합니다.
 */
public interface RelayRoomService {

    /**
     * 기존 익명 사용자를 방장으로 하는 새 릴레이 방을 생성합니다.
     */
    RelayRoomCreateResponse createRoom(String userUuidValue);

    /**
     * 기존 익명 사용자 기준으로 현재 릴레이 방 상태를 조회합니다.
     */
    RelayRoomStateResponse getRoomState(String userUuidValue, String roomCodeValue);
}
