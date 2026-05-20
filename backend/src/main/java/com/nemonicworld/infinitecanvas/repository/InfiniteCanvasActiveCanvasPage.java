package com.nemonicworld.infinitecanvas.repository;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import java.util.List;

public record InfiniteCanvasActiveCanvasPage(List<InfiniteCanvasState> items, long totalElements) {

    public InfiniteCanvasActiveCanvasPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
