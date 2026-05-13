package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.databind.JsonNode;

public record FlipbookMinFramesPerFlipbookSettings(int value) {

    public static final int DEFAULT_MIN_FRAMES_PER_FLIPBOOK = 8;

    public static FlipbookMinFramesPerFlipbookSettings defaultSettings() {
        return new FlipbookMinFramesPerFlipbookSettings(DEFAULT_MIN_FRAMES_PER_FLIPBOOK);
    }

    public static FlipbookMinFramesPerFlipbookSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException("플립북 최소 프레임 수 설정은 JSON 객체여야 합니다.");
        }

        int frames = requireInteger(value.get("value"), "value");
        if (frames <= 0) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException("플립북 최소 프레임 수는 0보다 커야 합니다.");
        }

        return new FlipbookMinFramesPerFlipbookSettings(frames);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException(
                "플립북 최소 프레임 수 설정 필드는 정수여야 합니다. field=" + fieldName);
        }

        return value.asInt();
    }
}
