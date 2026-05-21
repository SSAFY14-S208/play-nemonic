package com.nemonicworld.infinitecanvas.dto.websocket;

import java.time.LocalDateTime;

public record InfiniteCanvasEventResponse(InfiniteCanvasEventType type, String roomCode, Object data,
    LocalDateTime occurredAt) {

    public static InfiniteCanvasEventResponse of(InfiniteCanvasEventType type, String roomCode, Object data) {
        return new InfiniteCanvasEventResponse(type, roomCode, data, LocalDateTime.now());
    }
}
