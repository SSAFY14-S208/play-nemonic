package com.nemonicworld.clientlog.service.support;

import com.nemonicworld.clientlog.service.ClientLogSanitizer;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ClientLogEventEmitter {

    private static final Set<String> ERROR_EVENT_NAMES = Set.of("js_error", "unhandled_rejection",
        "client_network_failed");
    private static final List<String> STANDARD_FIELD_NAMES = List.of("flow_id", "session_id", "room_id", "prev_zone",
        "prev_path", "path", "referrer");

    private final ClientLogSanitizer sanitizer;

    public ClientLogEventEmitter(ClientLogSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public void emitClientEvent(Map<String, Object> event, String eventName) {
        Map<String, Object> metadata = sanitizedMap(event.get("metadata"));
        metadata.put("client_timestamp", sanitizer.sanitize(event.get("@timestamp")));

        StructuredEventLogger.clientEvent(ERROR_EVENT_NAMES.contains(eventName) ? "ERROR" : "INFO",
            text(event.get("service")), eventName, sanitizedText(event.get("message")), text(event.get("trace_id")),
            sanitizedText(event.get("content_type")), sanitizedText(event.get("uuid")), standardFields(event), metadata,
            sanitizedMap(event.get("error")));
    }

    public void logDropReasons(Map<String, Integer> dropReasons) {
        for (Map.Entry<String, Integer> entry : dropReasons.entrySet()) {
            logDropSummary(entry.getKey(), entry.getValue(), null);
        }
    }

    public void logDropSummary(String reason, int count, String ipAddress) {
        StructuredEventLogger.clientWarn("client_log_events_dropped", "client log events dropped",
            StructuredEventLogger.metadata("reason", reason, "count", count, "ip_address", ipAddress));
    }

    public void logUnlistedEvents(Map<String, Integer> unlistedEventNames) {
        for (Map.Entry<String, Integer> entry : unlistedEventNames.entrySet()) {
            StructuredEventLogger.clientWarn("client_log_event_unlisted", "client log event is not allow-listed",
                StructuredEventLogger.metadata("event_name", entry.getKey(), "count", entry.getValue(), "reason",
                    "unlisted_event_name"));
        }
    }

    private Map<String, Object> standardFields(Map<String, Object> event) {
        Map<String, Object> standardFields = new LinkedHashMap<>();
        for (String fieldName : STANDARD_FIELD_NAMES) {
            Object value = event.get(fieldName);
            if (value != null) {
                standardFields.put(fieldName, sanitizer.sanitize(value));
            }
        }

        return standardFields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizedMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return new LinkedHashMap<>();
        }

        return (Map<String, Object>) sanitizer.sanitize(mapValue);
    }

    private String sanitizedText(Object value) {
        if (value == null) {
            return null;
        }

        return sanitizer.sanitizeString(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
