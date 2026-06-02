package com.nemonicworld.backoffice.logs.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import org.springframework.stereotype.Component;

@Component
public class AdminLogsRequestReader {

    private final ObjectMapper objectMapper;

    public AdminLogsRequestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T readRequest(JsonNode requestNode, Class<T> type) {
        try {
            return objectMapper.treeToValue(requestNode, type);
        } catch (Exception e) {
            throw AdminLogsException.invalidQuery();
        }
    }
}
