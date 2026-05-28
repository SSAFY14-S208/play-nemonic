package com.nemonicworld.backoffice.setting.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SystemParameterValueSupport {

    private static final String INVALID_VALUE_MESSAGE = "시스템 파라미터 값을 직렬화하지 못했습니다.";
    private static final String REDACTED_VALUE = "[redacted]";
    private static final List<String> SENSITIVE_KEY_TOKENS = List.of("password", "secret", "token", "jwt",
        "authorization", "webhook", "smtp", "api_key", "apikey", "access_key", "refresh");

    private final ObjectMapper objectMapper;

    public SystemParameterValueSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseValue(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    public String serializeValue(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_VALUE_MESSAGE);
        }
    }

    public Object safeParameterValue(String key, String value) {
        if (isSensitiveParameterKey(key)) {
            return REDACTED_VALUE;
        }

        return parseValue(value);
    }

    public boolean isSensitiveParameterKey(String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "_");

        return SENSITIVE_KEY_TOKENS.stream().anyMatch(normalizedKey::contains);
    }
}
