package com.nemonicworld.relay.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelayRoomEventLogger {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Logger API_LOG = LoggerFactory.getLogger("logs.api");
    private static final Logger WEBSOCKET_LOG = LoggerFactory.getLogger("logs.websocket");
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("logs.audit");
    private static final Logger INTERNAL_LOG = LoggerFactory.getLogger(RelayRoomEventLogger.class);

    private static final String SERVICE_API = "backend-api";
    private static final String SERVICE_WEBSOCKET = "websocket-server";
    private static final String SERVICE_BACKOFFICE = "backoffice-api";

    private RelayRoomEventLogger() {
    }

    public static void apiBusiness(String eventName, Map<String, Object> metadata) {
        emit(API_LOG, "INFO", SERVICE_API, "business_event", eventName, eventName, metadata, null);
    }

    public static void websocketBusiness(String eventName, Map<String, Object> metadata) {
        emit(WEBSOCKET_LOG, "INFO", SERVICE_WEBSOCKET, "business_event", eventName, eventName, metadata, null);
    }

    public static void apiWarn(String eventName, String message, Map<String, Object> metadata, Throwable error) {
        emit(API_LOG, "WARN", SERVICE_API, "system_event", eventName, message, metadata, error);
    }

    public static void websocketWarn(String eventName, String message, Map<String, Object> metadata, Throwable error) {
        emit(WEBSOCKET_LOG, "WARN", SERVICE_WEBSOCKET, "system_event", eventName, message, metadata, error);
    }

    public static void audit(String eventName, Map<String, Object> metadata) {
        emit(AUDIT_LOG, "INFO", SERVICE_BACKOFFICE, "audit_event", eventName, eventName, metadata, null);
    }

    public static Map<String, Object> metadata(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata requires key-value pairs");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            metadata.put(String.valueOf(keyValues[index]), normalizeValue(keyValues[index + 1]));
        }

        return metadata;
    }

    private static void emit(Logger logger, String level, String service, String logType, String eventName,
        String message, Map<String, Object> metadata, Throwable error) {
        Map<String, Object> eventLog = new LinkedHashMap<>();
        eventLog.put("@timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        eventLog.put("level", level);
        eventLog.put("service", service);
        eventLog.put("log_type", logType);
        eventLog.put("event_name", eventName);
        eventLog.put("message", message);
        eventLog.put("metadata", metadata == null ? Map.of() : normalizeMap(metadata));
        if (error != null) {
            eventLog.put("error", metadata("type", error.getClass().getSimpleName(), "message", error.getMessage()));
        }

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(eventLog);
            write(logger, level, payload, error);
        } catch (JsonProcessingException e) {
            INTERNAL_LOG.error("relay_log_emit_failure event_name={}", eventName, e);
        }
    }

    private static void write(Logger logger, String level, String payload, Throwable error) {
        if ("ERROR".equals(level)) {
            logger.error(payload, error);
            return;
        }

        if ("WARN".equals(level)) {
            logger.warn(payload, error);
            return;
        }

        logger.info(payload);
    }

    private static Map<String, Object> normalizeMap(Map<String, Object> metadata) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }

        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }

        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        if (value instanceof UUID uuid) {
            return uuid.toString();
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }

            return normalized;
        }

        if (value instanceof Collection<?> collection) {
            return collection.stream().map(RelayRoomEventLogger::normalizeValue).toList();
        }

        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        return OBJECT_MAPPER.convertValue(value, Map.class);
    }
}
