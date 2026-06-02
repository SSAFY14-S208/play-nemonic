package com.nemonicworld.infinitecanvas.service.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasOperationApplier {

    public CanvasElementBatch createElementBatch(List<JsonNode> elements) {
        return new CanvasElementBatch(elements);
    }

    public InfiniteCanvasOperation createOperation(InfiniteCanvasOperationRequest request, String userUuid,
        long revision, LocalDateTime now) {
        String operationId = StringUtils.hasText(request.operationId())
            ? request.operationId().trim()
            : UUID.randomUUID().toString();
        JsonNode element = resolveOperationElement(request);
        String elementId = StringUtils.hasText(request.elementId())
            ? request.elementId().trim()
            : extractElementId(element);

        return new InfiniteCanvasOperation(operationId, request.clientOperationId().trim(), request.operationType(),
            elementId, element, request.payload(), userUuid, revision, now);
    }

    public String resolveOperationElementId(InfiniteCanvasOperationRequest operationRequest) {
        if (StringUtils.hasText(operationRequest.elementId())) {
            return operationRequest.elementId().trim();
        }

        return extractElementId(resolveOperationElement(operationRequest));
    }

    public void applyOperation(CanvasElementBatch elements, Map<String, InfiniteCanvasLock> locks,
        InfiniteCanvasOperation operation) {
        switch (operation.operationType()) {
            case CLEAR_CANVAS -> {
                elements.clear();
                locks.clear();
            }
            case DELETE_ELEMENT -> {
                removeElement(elements, operation.elementId());
                if (StringUtils.hasText(operation.elementId())) {
                    locks.remove(operation.elementId());
                }
            }
            case CREATE_ELEMENT, UPDATE_ELEMENT, UPSERT_ELEMENT ->
                upsertElement(elements, operation.elementId(), operation.element());
        }
    }

    private JsonNode resolveOperationElement(InfiniteCanvasOperationRequest request) {
        if (request.element() != null && !request.element().isNull()) {
            return request.element();
        }
        if (request.payload() != null && request.payload().has("element")) {
            return request.payload().get("element");
        }
        return null;
    }

    private void upsertElement(CanvasElementBatch elements, String elementId, JsonNode element) {
        if (element == null || element.isNull()) {
            return;
        }

        String resolvedElementId = StringUtils.hasText(elementId) ? elementId : extractElementId(element);
        if (!StringUtils.hasText(resolvedElementId)) {
            elements.add(element);
            return;
        }

        elements.upsert(resolvedElementId, element);
    }

    private void removeElement(CanvasElementBatch elements, String elementId) {
        if (!StringUtils.hasText(elementId)) {
            return;
        }

        elements.remove(elementId);
    }

    private String extractElementId(JsonNode element) {
        if (element == null || !element.isObject()) {
            return null;
        }
        if (StringUtils.hasText(element.path("id").asText(null))) {
            return element.path("id").asText();
        }
        if (StringUtils.hasText(element.path("elementId").asText(null))) {
            return element.path("elementId").asText();
        }
        return null;
    }

    public final class CanvasElementBatch {

        private final LinkedHashMap<String, JsonNode> elementsBySlot = new LinkedHashMap<>();
        private final Map<String, List<String>> slotKeysByElementId = new LinkedHashMap<>();
        private int nextSlotIndex;

        private CanvasElementBatch(List<JsonNode> elements) {
            for (JsonNode element : elements) {
                add(element);
            }
        }

        private void add(JsonNode element) {
            append(element, extractElementId(element));
        }

        private void upsert(String elementId, JsonNode element) {
            remove(elementId);
            append(element, elementId);
        }

        private void remove(String elementId) {
            List<String> slotKeys = slotKeysByElementId.remove(elementId);
            if (slotKeys == null) {
                return;
            }

            for (String slotKey : slotKeys) {
                elementsBySlot.remove(slotKey);
            }
        }

        private void clear() {
            elementsBySlot.clear();
            slotKeysByElementId.clear();
        }

        public List<JsonNode> toList() {
            return List.copyOf(elementsBySlot.values());
        }

        private void append(JsonNode element, String elementId) {
            String slotKey = nextSlotKey(elementId);
            elementsBySlot.put(slotKey, element);
            if (StringUtils.hasText(elementId)) {
                slotKeysByElementId.computeIfAbsent(elementId, unused -> new ArrayList<>()).add(slotKey);
            }
        }

        private String nextSlotKey(String elementId) {
            String prefix = StringUtils.hasText(elementId) ? "element:" + elementId : "anonymous";
            return prefix + ":" + nextSlotIndex++;
        }
    }
}
