package com.nemonicworld.infinitecanvas.redis;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record InfiniteCanvasCursor(String userUuid, Double x, Double y, Double zoom, JsonNode payload,
    LocalDateTime updatedAt) {
}
