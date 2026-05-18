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

    void delete(String roomCode);
}
