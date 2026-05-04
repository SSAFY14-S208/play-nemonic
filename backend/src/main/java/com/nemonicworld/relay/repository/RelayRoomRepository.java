package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.entity.RelayRoomState;

/**
 * 진행 중 릴레이 방 상태 저장소가 제공해야 하는 최소 동작입니다.
 */
public interface RelayRoomRepository {

    /**
     * 새 방코드 발급 전 같은 방코드가 이미 사용 중인지 확인합니다.
     */
    boolean existsByRoomCode(String roomCode);

    /**
     * 생성된 릴레이 방 상태를 저장합니다.
     */
    void save(RelayRoomState roomState);
}
