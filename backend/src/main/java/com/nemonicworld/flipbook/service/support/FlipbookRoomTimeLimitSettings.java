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
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting allowed values must not be empty.");
        }

        allowedSeconds = Collections.unmodifiableSet(new LinkedHashSet<>(allowedSeconds));
        validateSeconds(defaultSeconds, "default");
        for (Integer allowedSecond : allowedSeconds) {
            if (allowedSecond == null) {
                throw new InvalidFlipbookRoomTimeLimitSettingsException(
                    "Flipbook room time limit setting allowed value must be an integer.");
            }
            validateSeconds(allowedSecond, "allowed");
        }
        if (!allowedSeconds.contains(defaultSeconds)) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit default value must be included in allowed values.");
        }
    }

    public static FlipbookRoomTimeLimitSettings defaultSettings() {
        return new FlipbookRoomTimeLimitSettings(DEFAULT_TIME_LIMIT_SECONDS, Set.of(30, 45, 60));
    }

    public static FlipbookRoomTimeLimitSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting must be a JSON object.");
        }

        int defaultSeconds = requireInteger(value.get("default"), "default");
        JsonNode allowed = value.get("allowed");
        if (allowed == null || !allowed.isArray() || allowed.isEmpty()) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting allowed values must be a non-empty array.");
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
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting field must be an integer: " + fieldName);
        }

        return value.asInt();
    }

    private static void validateSeconds(int seconds, String fieldName) {
        if (seconds < MIN_CONFIGURABLE_TIME_LIMIT_SECONDS) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting field is too small: " + fieldName);
        }

        if (seconds > MAX_CONFIGURABLE_TIME_LIMIT_SECONDS) {
            throw new InvalidFlipbookRoomTimeLimitSettingsException(
                "Flipbook room time limit setting field exceeds the configurable upper bound: " + fieldName);
        }
    }
}
