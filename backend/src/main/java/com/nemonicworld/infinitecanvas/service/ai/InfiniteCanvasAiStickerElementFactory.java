package com.nemonicworld.infinitecanvas.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InfiniteCanvasAiStickerElementFactory {

    private static final int DEFAULT_ELEMENT_SIZE = 240;

    private final ObjectMapper objectMapper;

    public InfiniteCanvasAiStickerElementFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode createElement(UUID stickerId, String imageUrl, String objectKey, String prompt, String style,
        int width, int height, String promptVersion) {
        ObjectNode element = objectMapper.createObjectNode();
        element.put("id", "ai-sticker-%s".formatted(stickerId));
        element.put("type", "image");
        element.put("src", imageUrl);
        element.put("objectKey", objectKey);
        element.put("width", DEFAULT_ELEMENT_SIZE);
        element.put("height", Math.max(1, Math.round(DEFAULT_ELEMENT_SIZE * (height / (float) width))));
        element.put("naturalWidth", width);
        element.put("naturalHeight", height);
        ObjectNode metadata = element.putObject("metadata");
        metadata.put("source", "ai_sticker");
        metadata.put("prompt", prompt);
        metadata.put("style", style);
        metadata.put("promptVersion", promptVersion);
        metadata.put("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());

        return element;
    }
}
