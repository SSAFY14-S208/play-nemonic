package com.nemonicworld.relay.repository;

import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 진행 중 릴레이 방 상태 저장소가 제공해야 하는 최소 동작입니다.
 */
public interface RelayRoomRepository {

    // 방 종료/정리 정책이 확정되기 전까지 임시 이미지 fallback 정리 기준과 같은 보수적 TTL을 사용합니다.
    Duration ROOM_STATE_TTL = Duration.ofHours(24);

    /**
     * 새 방코드 발급 때 같은 방코드가 이미 사용 중인지 확인합니다.
     */
    boolean existsByRoomCode(String roomCode);

    /**
     * 생성된 릴레이 방 상태를 저장합니다.
     */
    void save(RelayRoomState roomState);

    /**
     * Redis에 저장된 현재 방 상태가 기대한 값과 같을 때만 갱신합니다.
     */
    boolean saveIfUnchanged(RelayRoomState expectedRoomState, RelayRoomState updatedRoomState);

    /**
     * 방코드로 Redis에 저장된 릴레이 방 상태를 조회합니다.
     */
    Optional<RelayRoomState> findByRoomCode(String roomCode);

    /**
     * 현재 파트 마감 시각이 지난 PLAYING 방을 최대 limit개 조회합니다.
     */
    List<RelayRoomState> findExpiredPlayingRooms(LocalDateTime now, int limit);

    /**
     * 재접속 유예 만료 또는 이탈 확정 참여자의 현재 파트 자동 제출이 필요한 PLAYING 방을 최대 limit개 조회합니다.
     */
    List<RelayRoomState> findPlayingRoomsForDisconnectGrace(LocalDateTime disconnectCutoff, int limit);

    /**
     * WAITING 상태에서 모든 참여자가 끊긴 채 idleCutoff 이전부터 방치된 방을 조회합니다.
     */
    List<RelayRoomState> findAbandonedWaitingRooms(LocalDateTime idleCutoff, int limit);

    /**
     * PLAYING 상태에서 모든 참여자가 끊겼거나 dropped 처리된 채 abandonedCutoff 이전부터 방치된 방을 조회합니다.
     */
    List<RelayRoomState> findAbandonedPlayingRooms(LocalDateTime abandonedCutoff, int limit);

    /**
     * Redis에는 연결 중으로 남았지만 실제 WebSocket 세션 여부를 재확인해야 하는 WAITING/PLAYING 방을 조회합니다.
     */
    List<RelayRoomState> findRoomsForConnectionReconciliation(int limit);

    /**
     * 비정상 상태 보정을 위해 참여자가 비어 있는 WAITING 방을 조회합니다.
     */
    List<RelayRoomState> findEmptyWaitingRooms(int limit);

    /**
     * 최종 결과물 생성이 필요한 FINALIZING 방을 최대 limit개 조회합니다.
     */
    List<RelayRoomState> findFinalizingRooms(int limit);

    /**
     * 결과 생성이 끝난 뒤 close 기준 시각을 지난 FINISHED 방을 최대 limit개 조회합니다.
     */
    List<RelayRoomState> findClosableFinishedRooms(LocalDateTime closeCutoff, int limit);

    /**
     * 임시 파일 정리 대상인 CLOSED 릴레이 방을 최대 limit개 조회합니다.
     */
    List<RelayRoomState> findClosedRooms(int limit);

    /**
     * 백오피스 관리 화면용 — CLOSED를 제외한 모든 활성 릴레이 방(WAITING/PLAYING/FINALIZING/FINISHED)을
     * 조회합니다.
     */
    List<RelayRoomState> findAllActiveRooms();

    /**
     * 백오피스와 메트릭 수집에서 필요한 상태의 활성 릴레이 방만 조회합니다.
     */
    List<RelayRoomState> findActiveRoomsByStatuses(Set<RelayRoomStatus> statuses);

    /**
     * 백오피스 목록 조회용으로 상태 필터와 페이지 범위를 반영해 활성 릴레이 방을 조회합니다.
     */
    RelayActiveRoomPage findActiveRoomsByStatuses(Set<RelayRoomStatus> statuses, int page, int size);

    /**
     * 메트릭 수집용으로 상태 필터에 해당하는 활성 릴레이 방 수만 조회합니다.
     */
    long countActiveRoomsByStatuses(Set<RelayRoomStatus> statuses);

    /**
     * 같은 방 최종화가 여러 서버에서 동시에 실행되지 않도록 짧은 Redis lock을 획득합니다.
     */
    boolean acquireFinalizationLock(String roomCode, String token, Duration ttl);

    /**
     * 최종화 처리 후 Redis lock을 해제합니다.
     */
    void releaseFinalizationLock(String roomCode, String token);

    /**
     * CLOSED 방의 임시 파일 정리가 끝났는지 별도 marker key로 확인합니다.
     */
    boolean isTempCleanupMarked(String roomCode);

    /**
     * CLOSED 방의 임시 파일 정리 완료 marker를 저장합니다.
     */
    void markTempCleanup(String roomCode, LocalDateTime cleanedAt, Duration ttl);

    /**
     * 같은 CLOSED 방의 임시 파일 정리를 중복 실행하지 않도록 lock을 획득합니다.
     */
    boolean acquireTempCleanupLock(String roomCode, Duration ttl);

    /**
     * CLOSED 방 임시 파일 정리 lock을 해제합니다.
     */
    void releaseTempCleanupLock(String roomCode);
}
