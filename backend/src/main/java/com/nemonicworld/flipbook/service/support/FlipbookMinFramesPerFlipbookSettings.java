package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.databind.JsonNode;

public record FlipbookMinFramesPerFlipbookSettings(int value) {

    public static final int DEFAULT_MIN_FRAMES_PER_FLIPBOOK = 8;

    public static FlipbookMinFramesPerFlipbookSettings defaultSettings() {
        return new FlipbookMinFramesPerFlipbookSettings(DEFAULT_MIN_FRAMES_PER_FLIPBOOK);
    }

    public static FlipbookMinFramesPerFlipbookSettings fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException(
                "Flipbook min frames setting must be a JSON object.");
        }

        int frames = requireInteger(value.get("value"), "value");
        if (frames <= 0) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException(
                "Flipbook min frames value must be greater than zero.");
        }

        return new FlipbookMinFramesPerFlipbookSettings(frames);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidFlipbookMinFramesPerFlipbookSettingsException(
                "Flipbook min frames setting field must be an integer: " + fieldName);
        }

        return value.asInt();
    }
}
