package com.nemonicworld.infinitecanvas.repository;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface InfiniteCanvasRepository {

    Duration CANVAS_STATE_TTL = Duration.ofHours(24);

    void save(InfiniteCanvasState canvasState);

    boolean saveIfUnchanged(InfiniteCanvasState expectedCanvasState, InfiniteCanvasState updatedCanvasState);

    boolean saveIfUnchangedAndAppendOperations(InfiniteCanvasState expectedCanvasState,
        InfiniteCanvasState updatedCanvasState, List<InfiniteCanvasOperation> operations);

    Optional<InfiniteCanvasState> findByRoomCode(String roomCode);

    InfiniteCanvasActiveCanvasPage findActiveCanvases(int page, int size);

    List<InfiniteCanvasState> findAllActiveCanvases();

    /**
     * connected 참여자가 한 명도 없고 마지막 갱신이 {@code idleCutoff} 이전인
     * ACTIVE 캔버스 후보를 SCAN으로 모은다. 빈 방을 주기적으로 정리하기 위한
     * 스케줄러용 조회 — 릴레이/플립북의 {@code findEmptyWaitingRooms} 패턴과 동일.
     */
    List<InfiniteCanvasState> findAbandonedActiveCanvases(java.time.LocalDateTime idleCutoff, int limit);

    void delete(String roomCode);
}
