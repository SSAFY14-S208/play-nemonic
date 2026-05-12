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
            throw new InvalidFlipbookRoomParticipantLimitException("플립북 참여 인원 설정은 JSON 객체여야 합니다.");
        }

        int minParticipants = requireInteger(value.get("min"), "min");
        int maxParticipants = requireInteger(value.get("max"), "max");
        validate(minParticipants, maxParticipants);

        return new FlipbookRoomParticipantLimit(minParticipants, maxParticipants);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidFlipbookRoomParticipantLimitException("플립북 참여 인원 설정 필드는 정수여야 합니다. field=" + fieldName);
        }

        return value.asInt();
    }

    private static void validate(int minParticipants, int maxParticipants) {
        if (minParticipants < MIN_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidFlipbookRoomParticipantLimitException("플립북 최소 참여 인원은 2명 이상이어야 합니다.");
        }

        if (maxParticipants < minParticipants) {
            throw new InvalidFlipbookRoomParticipantLimitException("플립북 최대 참여 인원은 최소 참여 인원보다 크거나 같아야 합니다.");
        }

        if (maxParticipants > MAX_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidFlipbookRoomParticipantLimitException("플립북 최대 참여 인원이 설정 가능한 상한을 초과했습니다.");
        }
    }
}
