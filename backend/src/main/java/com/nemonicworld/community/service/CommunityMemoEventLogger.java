package com.nemonicworld.community.service;

import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 캔버스 도메인 이벤트를 공통 구조화 로그 스키마로 발행합니다.
 */
public final class CommunityMemoEventLogger {

    private static final String CONTENT_TYPE = "community_memo";
    private static final int TEXT_PREVIEW_LIMIT = 120;

    private CommunityMemoEventLogger() {
    }

    public static void business(String eventName, UUID actorUuid, Map<String, Object> metadata) {
        StructuredEventLogger.apiBusiness(eventName, CONTENT_TYPE, stringify(actorUuid),
            enrichUserMetadata(actorUuid, metadata));
    }

    public static void business(String eventName, Map<String, Object> metadata) {
        StructuredEventLogger.apiBusiness(eventName, CONTENT_TYPE, null, enrichSystemMetadata(metadata));
    }

    public static void warn(String eventName, String message, UUID actorUuid, Map<String, Object> metadata,
        Throwable error) {
        StructuredEventLogger.apiWarn(eventName, message, enrichUserMetadata(actorUuid, metadata), error);
    }

    public static void warn(String eventName, String message, Map<String, Object> metadata, Throwable error) {
        StructuredEventLogger.apiWarn(eventName, message, enrichSystemMetadata(metadata), error);
    }

    public static Map<String, Object> metadata(Object... keyValues) {
        return StructuredEventLogger.metadata(keyValues);
    }

    public static String sourceType(UUID artifactId) {
        return artifactId == null ? "DIRECT" : "GALLERY";
    }

    public static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public static int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    public static String hash(String value) {
        return StructuredEventLogger.sha256Prefix(value);
    }

    public static String textPreview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String compactValue = value.replaceAll("\\s+", " ").trim();
        if (compactValue.length() <= TEXT_PREVIEW_LIMIT) {
            return compactValue;
        }

        return compactValue.substring(0, TEXT_PREVIEW_LIMIT) + "...";
    }

    private static String stringify(UUID value) {
        return value == null ? null : value.toString();
    }

    private static Map<String, Object> enrichUserMetadata(UUID actorUuid, Map<String, Object> metadata) {
        Map<String, Object> enrichedMetadata = new LinkedHashMap<>();
        enrichedMetadata.put("actor_type", actorUuid == null ? "anonymous" : "user");
        enrichedMetadata.put("user_uuid", stringify(actorUuid));
        enrichedMetadata.put("actor_user_uuid", stringify(actorUuid));
        enrichedMetadata.putAll(metadata == null ? Map.of() : metadata);

        return enrichedMetadata;
    }

    private static Map<String, Object> enrichSystemMetadata(Map<String, Object> metadata) {
        Map<String, Object> enrichedMetadata = new LinkedHashMap<>();
        enrichedMetadata.put("actor_type", "system");
        enrichedMetadata.putAll(metadata == null ? Map.of() : metadata);

        return enrichedMetadata;
    }
}
