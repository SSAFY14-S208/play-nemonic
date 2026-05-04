package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;

public interface RelayRoomService {

    RelayRoomCreateResponse createRoom(String userUuidValue);
}
