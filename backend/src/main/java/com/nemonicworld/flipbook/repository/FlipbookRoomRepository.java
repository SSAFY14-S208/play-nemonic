package com.nemonicworld.flipbook.repository;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.Duration;
import java.util.Optional;

/**
 * 진행 중 플립북 방 상태 저장소가 제공해야 하는 동작입니다.
 */
public interface FlipbookRoomRepository {

    // 릴레이와 동일하게 방 상태와 초대코드 메타데이터를 24시간 동안 유지합니다.
    Duration ROOM_STATE_TTL = Duration.ofHours(24);

    /**
     * 새 방코드 발급 때 같은 방코드가 이미 사용 중인지 확인합니다.
     */
    boolean existsByRoomCode(String roomCode);

    /**
     * 생성된 플립북 방 상태를 저장합니다.
     */
    void save(FlipbookRoomState roomState);

    /**
     * Redis에 저장된 현재 방 상태가 기대한 값과 같을 때만 갱신합니다.
     */
    boolean saveIfUnchanged(FlipbookRoomState expectedRoomState, FlipbookRoomState updatedRoomState);

    /**
     * 방코드로 Redis에 저장된 플립북 방 상태를 조회합니다.
     */
    Optional<FlipbookRoomState> findByRoomCode(String roomCode);
}
