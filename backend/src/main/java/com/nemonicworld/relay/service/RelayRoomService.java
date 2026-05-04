package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;

/**
 * 릴레이 방 유스케이스를 정의합니다.
 */
public interface RelayRoomService {

    /**
     * 기존 익명 사용자를 방장으로 하는 새 릴레이 방을 생성합니다.
     */
    RelayRoomCreateResponse createRoom(String userUuidValue);
}
