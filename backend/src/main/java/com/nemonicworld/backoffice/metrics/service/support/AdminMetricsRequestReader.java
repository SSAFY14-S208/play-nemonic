package com.nemonicworld.backoffice.metrics.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import org.springframework.stereotype.Component;

@Component
public class AdminMetricsRequestReader {

    private final ObjectMapper objectMapper;

    public AdminMetricsRequestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T readRequest(JsonNode requestNode, Class<T> type) {
        if (requestNode == null || !requestNode.isObject()) {
            throw AdminMetricsException.invalidQuery();
        }
        try {
            return objectMapper.treeToValue(requestNode, type);
        } catch (Exception e) {
            throw AdminMetricsException.invalidQuery();
        }
    }
}
