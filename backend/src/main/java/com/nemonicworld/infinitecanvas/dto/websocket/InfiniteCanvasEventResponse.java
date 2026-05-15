package com.nemonicworld.infinitecanvas.dto.websocket;

import java.time.LocalDateTime;

public record InfiniteCanvasEventResponse(InfiniteCanvasEventType type, String canvasId, Object data,
    LocalDateTime occurredAt) {

    public static InfiniteCanvasEventResponse of(InfiniteCanvasEventType type, String canvasId, Object data) {
        return new InfiniteCanvasEventResponse(type, canvasId, data, LocalDateTime.now());
    }
}
