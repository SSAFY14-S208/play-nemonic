package com.nemonicworld.infinitecanvas.redis;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record InfiniteCanvasOperation(String operationId, String clientOperationId,
    InfiniteCanvasOperationType operationType, String elementId, JsonNode element, JsonNode payload, String userUuid,
    long revision, LocalDateTime occurredAt) {
}
