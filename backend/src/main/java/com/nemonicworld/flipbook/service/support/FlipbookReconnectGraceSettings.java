package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

public record FlipbookReconnectGraceSettings(long seconds) {

    public static final long DEFAULT_RECONNECT_GRACE_SECONDS = 10L;
    public static final long MIN_CONFIGURABLE_RECONNECT_GRACE_SECONDS = 0L;
    public static final long MAX_CONFIGURABLE_RECONNECT_GRACE_SECONDS = 300L;

    public static FlipbookReconnectGraceSettings defaultSettings() {
        return new FlipbookReconnectGraceSettings(DEFAULT_RECONNECT_GRACE_SECONDS);
    }

    public static FlipbookReconnectGraceSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookReconnectGraceSettingsException("플립북 재연결 유예 시간 설정은 JSON 객체여야 합니다.");
        }

        long seconds = requireLong(value.get("value"), "value");
        validate(seconds);

        return new FlipbookReconnectGraceSettings(seconds);
    }

    public Duration period() {
        return Duration.ofSeconds(seconds);
    }

    private static long requireLong(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new InvalidFlipbookReconnectGraceSettingsException(
                "플립북 재연결 유예 시간 설정 필드는 정수여야 합니다. field=" + fieldName);
        }

        return value.asLong();
    }

    private static void validate(long seconds) {
        if (seconds < MIN_CONFIGURABLE_RECONNECT_GRACE_SECONDS) {
            throw new InvalidFlipbookReconnectGraceSettingsException("플립북 재연결 유예 시간은 0초 이상이어야 합니다.");
        }

        if (seconds > MAX_CONFIGURABLE_RECONNECT_GRACE_SECONDS) {
            throw new InvalidFlipbookReconnectGraceSettingsException("플립북 재연결 유예 시간이 설정 가능한 상한을 초과했습니다.");
        }
    }
}
