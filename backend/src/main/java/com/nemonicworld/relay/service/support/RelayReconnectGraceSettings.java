package com.nemonicworld.relay.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

public record RelayReconnectGraceSettings(long seconds) {

    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = 10L;
    public static final long MIN_CONFIGURABLE_RECONNECT_GRACE_SECONDS = 0L;
    public static final long MAX_CONFIGURABLE_RECONNECT_GRACE_SECONDS = 300L;

    public static RelayReconnectGraceSettings defaultSettings() {
        return new RelayReconnectGraceSettings(DEFAULT_RECONNECT_GRACE_SECONDS);
    }

    public static RelayReconnectGraceSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidRelayReconnectGraceSettingsException(
                "Relay reconnect grace setting must be a JSON object.");
        }

        long seconds = requireLong(value.get("value"), "value");
        validate(seconds);

        return new RelayReconnectGraceSettings(seconds);
    }

    public Duration period() {
        return Duration.ofSeconds(seconds);
    }

    private static long requireLong(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new InvalidRelayReconnectGraceSettingsException(
                "Relay reconnect grace setting field must be an integer: " + fieldName);
        }

        return value.asLong();
    }

    private static void validate(long seconds) {
        if (seconds < MIN_CONFIGURABLE_RECONNECT_GRACE_SECONDS) {
            throw new InvalidRelayReconnectGraceSettingsException(
                "Relay reconnect grace seconds must be greater than or equal to zero.");
        }

        if (seconds > MAX_CONFIGURABLE_RECONNECT_GRACE_SECONDS) {
            throw new InvalidRelayReconnectGraceSettingsException(
                "Relay reconnect grace seconds exceeds the configurable upper bound.");
        }
    }
}
