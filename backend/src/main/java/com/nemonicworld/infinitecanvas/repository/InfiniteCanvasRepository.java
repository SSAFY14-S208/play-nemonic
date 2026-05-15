package com.nemonicworld.infinitecanvas.repository;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface InfiniteCanvasRepository {

    Duration CANVAS_STATE_TTL = Duration.ofHours(24);

    void save(InfiniteCanvasState canvasState);

    boolean saveIfUnchanged(InfiniteCanvasState expectedCanvasState, InfiniteCanvasState updatedCanvasState);

    Optional<InfiniteCanvasState> findByCanvasId(String canvasId);

    List<InfiniteCanvasState> findAllActiveCanvases();

    void delete(String canvasId);
}
