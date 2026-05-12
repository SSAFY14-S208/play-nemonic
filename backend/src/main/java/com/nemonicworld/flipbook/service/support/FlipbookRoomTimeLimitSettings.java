package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record FlipbookRoomTimeLimitSettings(int defaultSeconds, Set<Integer> allowedSeconds) {

    public static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    public static final int MIN_CONFIGURABLE_TIME_LIMIT_SECONDS = 5;
    public static final int MAX_CONFIGURABLE_TIME_LIMIT_SECONDS = 600;

    public FlipbookRoomTimeLimitSettings {
        if (allowedSeconds == null || allowedSeconds.isEmpty()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 허용값은 비어 있을 수 없습니다.");
        }

        allowedSeconds = Collections.unmodifiableSet(new LinkedHashSet<>(allowedSeconds));
        validateSeconds(defaultSeconds, "default");
        for (Integer allowedSecond : allowedSeconds) {
            if (allowedSecond == null) {
                throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 허용값은 정수여야 합니다.");
            }
            validateSeconds(allowedSecond, "allowed");
        }
        if (!allowedSeconds.contains(defaultSeconds)) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 기본 제한 시간은 허용값에 포함되어야 합니다.");
        }
    }

    public static FlipbookRoomTimeLimitSettings defaultSettings() {
        return new FlipbookRoomTimeLimitSettings(DEFAULT_TIME_LIMIT_SECONDS, Set.of(30, 45, 60));
    }

    public static FlipbookRoomTimeLimitSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 설정은 JSON 객체여야 합니다.");
        }

        int defaultSeconds = requireInteger(value.get("default"), "default");
        JsonNode allowed = value.get("allowed");
        if (allowed == null || !allowed.isArray() || allowed.isEmpty()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 허용값은 비어 있지 않은 배열이어야 합니다.");
        }

        Set<Integer> allowedSeconds = new LinkedHashSet<>();
        for (JsonNode option : allowed) {
            allowedSeconds.add(requireInteger(option, "allowed"));
        }

        return new FlipbookRoomTimeLimitSettings(defaultSeconds, allowedSeconds);
    }

    public boolean allows(int seconds) {
        return allowedSeconds.contains(seconds);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 설정 필드는 정수여야 합니다. field=" + fieldName);
        }

        return value.asInt();
    }

    private static void validateSeconds(int seconds, String fieldName) {
        if (seconds < MIN_CONFIGURABLE_TIME_LIMIT_SECONDS) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException("플립북 방 제한 시간 설정값이 너무 작습니다. field=" + fieldName);
        }

        if (seconds > MAX_CONFIGURABLE_TIME_LIMIT_SECONDS) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "플립북 방 제한 시간 설정값이 설정 가능한 상한을 초과했습니다. field=" + fieldName);
        }
    }
}
