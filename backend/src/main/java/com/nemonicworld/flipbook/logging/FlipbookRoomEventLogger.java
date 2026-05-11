package com.nemonicworld.flipbook.logging;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.Map;

public final class FlipbookRoomEventLogger {

    private FlipbookRoomEventLogger() {
    }

    public static void apiBusiness(String eventName, Map<String, Object> metadata) {
        StructuredEventLogger.apiBusiness(eventName, metadata);
    }

    public static void websocketBusiness(String eventName, Map<String, Object> metadata) {
        StructuredEventLogger.websocketBusiness(eventName, metadata);
    }

    public static void apiWarn(String eventName, String message, Map<String, Object> metadata, Throwable error) {
        StructuredEventLogger.apiWarn(eventName, message, metadata, error);
    }

    public static void websocketWarn(String eventName, String message, Map<String, Object> metadata, Throwable error) {
        StructuredEventLogger.websocketWarn(eventName, message, metadata, error);
    }

    public static void audit(String eventName, Map<String, Object> metadata) {
        StructuredEventLogger.audit(eventName, metadata);
    }

    public static Map<String, Object> metadata(Object... keyValues) {
        return StructuredEventLogger.metadata(keyValues);
    }
}
