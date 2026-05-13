package com.nemonicworld.fortune.logging;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.Map;
import java.util.UUID;

public final class FortuneEventLogger {

    private static final String CONTENT_TYPE_FORTUNE = "fortune";

    private FortuneEventLogger() {
    }

    public static void apiBusiness(String eventName, UUID userUuid, Map<String, Object> metadata) {
        StructuredEventLogger.apiBusiness(eventName, CONTENT_TYPE_FORTUNE, userUuid.toString(),
            withUuid(userUuid, metadata));
    }

    public static void apiBusinessWarn(String eventName, String message, UUID userUuid, Map<String, Object> metadata,
        Throwable error) {
        StructuredEventLogger.apiBusinessWarn(eventName, CONTENT_TYPE_FORTUNE, userUuid.toString(), message,
            withUuid(userUuid, metadata), error);
    }

    public static Map<String, Object> metadata(Object... keyValues) {
        return StructuredEventLogger.metadata(keyValues);
    }

    private static Map<String, Object> withUuid(UUID userUuid, Map<String, Object> metadata) {
        Map<String, Object> values = StructuredEventLogger.metadata("uuid", userUuid);
        values.putAll(metadata);

        return values;
    }
}
