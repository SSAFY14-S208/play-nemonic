package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.entity.RelayRoomState;

public interface RelayRoomRepository {

    boolean existsByRoomCode(String roomCode);

    void save(RelayRoomState roomState);
}
