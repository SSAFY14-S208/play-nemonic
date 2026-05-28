package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

public final class SystemParameterJsonReader {

    private SystemParameterJsonReader() {
    }

    public static JsonNode readTree(ObjectMapper objectMapper, String settingValue) throws JsonProcessingException {
        return objectMapper.readTree(settingValue);
    }

    public static Optional<Integer> readPositiveIntegerValue(ObjectMapper objectMapper, String settingValue)
        throws JsonProcessingException {
        JsonNode root = readTree(objectMapper, settingValue);
        JsonNode value = root == null || !root.isObject() ? null : root.get("value");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            return Optional.empty();
        }

        return Optional.of(value.asInt());
    }
}
