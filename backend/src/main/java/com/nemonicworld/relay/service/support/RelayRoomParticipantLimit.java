package com.nemonicworld.relay.service.support;

import com.fasterxml.jackson.databind.JsonNode;

public record RelayRoomParticipantLimit(int minParticipants, int maxParticipants) {

    public static final int DEFAULT_MIN_PARTICIPANTS = 2;
    public static final int DEFAULT_MAX_PARTICIPANTS = 6;
    public static final int MIN_CONFIGURABLE_PARTICIPANTS = 2;
    public static final int MAX_CONFIGURABLE_PARTICIPANTS = 20;

    public static RelayRoomParticipantLimit defaultLimit() {
        return new RelayRoomParticipantLimit(DEFAULT_MIN_PARTICIPANTS, DEFAULT_MAX_PARTICIPANTS);
    }

    public static RelayRoomParticipantLimit fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Relay participant limit setting must be a JSON object.");
        }

        int minParticipants = requireInteger(value.get("min"), "min");
        int maxParticipants = requireInteger(value.get("max"), "max");
        validate(minParticipants, maxParticipants);

        return new RelayRoomParticipantLimit(minParticipants, maxParticipants);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(
                "Relay participant limit setting field must be an integer: " + fieldName);
        }

        return value.asInt();
    }

    private static void validate(int minParticipants, int maxParticipants) {
        if (minParticipants < MIN_CONFIGURABLE_PARTICIPANTS) {
            throw new IllegalArgumentException("Relay minimum participants must be at least 2.");
        }

        if (maxParticipants < minParticipants) {
            throw new IllegalArgumentException("Relay maximum participants must be greater than or equal to minimum.");
        }

        if (maxParticipants > MAX_CONFIGURABLE_PARTICIPANTS) {
            throw new IllegalArgumentException("Relay maximum participants exceeds the configurable upper bound.");
        }
    }
}
