package com.nemonicworld.flipbook.repository;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 백오피스 관리 화면용 — CLOSED를 제외한 모든 활성 플립북 방(WAITING/PLAYING/FINALIZING/FINISHED)을
     * 조회합니다.
     */
    List<FlipbookRoomState> findAllActiveRooms();

    /**
     * 게임 중 재접속 유예가 만료된 참여자가 있는 방을 조회합니다.
     */
    List<FlipbookRoomState> findPlayingRoomsForDisconnectGrace(LocalDateTime disconnectCutoff, int limit);

    /**
     * WAITING 상태에서 모든 참여자가 끊긴 채 idleCutoff 이전부터 방치된 방을 조회합니다.
     */
    List<FlipbookRoomState> findAbandonedWaitingRooms(LocalDateTime idleCutoff, int limit);

    /**
     * PLAYING 상태에서 모든 참여자가 끊겼거나 dropped 처리된 채 abandonedCutoff 이전부터 방치된 방을 조회합니다.
     */
    List<FlipbookRoomState> findAbandonedPlayingRooms(LocalDateTime abandonedCutoff, int limit);

    /**
     * 비정상 상태 보정을 위해 참여자가 비어 있는 WAITING 방을 조회합니다.
     */
    List<FlipbookRoomState> findEmptyWaitingRooms(int limit);

    /**
     * 현재 라운드 마감 시각이 지난 PLAYING 방을 최대 limit개 조회합니다.
     */
    List<FlipbookRoomState> findExpiredPlayingRooms(LocalDateTime roundDeadlineCutoff, int limit);

    /**
     * 최종 결과물 생성을 기다리는 FINALIZING 방을 최대 limit개 조회합니다.
     */
    List<FlipbookRoomState> findFinalizingRooms(int limit);

    /**
     * 결과 생성이 끝난 뒤 close 기준 시각을 지난 FINISHED 방을 최대 limit개 조회합니다.
     */
    List<FlipbookRoomState> findClosableFinishedRooms(LocalDateTime closeCutoff, int limit);

    /**
     * 특정 방의 최종 결과물 생성 lock을 획득합니다.
     */
    boolean acquireFinalizationLock(String roomCode, String token, Duration ttl);

    /**
     * 토큰이 일치할 때만 특정 방의 최종 결과물 생성 lock을 해제합니다.
     */
    void releaseFinalizationLock(String roomCode, String token);
}
