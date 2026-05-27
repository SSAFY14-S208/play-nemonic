package com.nemonicworld.gallery.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GalleryMetaSupport {

    private static final String INVALID_META_MESSAGE = "메타데이터 형식이 올바르지 않습니다.";
    private static final String EMPTY_META_JSON = "{}";
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public GalleryMetaSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parseGalleryMeta(String meta) {
        if (!StringUtils.hasText(meta)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedMeta = objectMapper.readValue(meta, META_TYPE);
            return parsedMeta == null ? Map.of() : parsedMeta;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public String serializePhoneDrawingMeta(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return EMPTY_META_JSON;
        }

        if (!meta.isObject()) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }
    }
}
