package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.databind.JsonNode;

public record FlipbookRoomParticipantLimit(int minParticipants, int maxParticipants) {

    public static final int DEFAULT_MIN_PARTICIPANTS = 2;
    public static final int DEFAULT_MAX_PARTICIPANTS = 6;
    public static final int MIN_CONFIGURABLE_PARTICIPANTS = 2;
    public static final int MAX_CONFIGURABLE_PARTICIPANTS = 20;

    public static FlipbookRoomParticipantLimit defaultLimit() {
        return new FlipbookRoomParticipantLimit(DEFAULT_MIN_PARTICIPANTS, DEFAULT_MAX_PARTICIPANTS);
    }

    public static FlipbookRoomParticipantLimit fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidFlipbookRoomParticipantLimitException(
                "Flipbook participant limit setting must be a JSON object.");
        }

        int minParticipants = requireInteger(value.get("min"), "min");
        int maxParticipants = requireInteger(value.get("max"), "max");
        validate(minParticipants, maxParticipants);

        return new FlipbookRoomParticipantLimit(minParticipants, maxParticipants);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidFlipbookRoomParticipantLimitException(
                "Flipbook participant limit setting field must be an integer: " + fieldName);
        }

        return value.asInt();
    }

    private static void validate(int minParticipants, int maxParticipants) {
        if (minParticipants < MIN_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidFlipbookRoomParticipantLimitException(
                "Flipbook minimum participants must be at least 2.");
        }

        if (maxParticipants < minParticipants) {
            throw new InvalidFlipbookRoomParticipantLimitException(
                "Flipbook maximum participants must be greater than or equal to minimum.");
        }

        if (maxParticipants > MAX_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidFlipbookRoomParticipantLimitException(
                "Flipbook maximum participants exceeds the configurable upper bound.");
        }
    }
}
