package com.nemonicworld.infinitecanvas.service.support;

import com.fasterxml.jackson.databind.JsonNode;

public record InfiniteCanvasParticipantLimit(int minParticipants, int maxParticipants) {

    public static final int DEFAULT_MIN_PARTICIPANTS = 1;
    public static final int DEFAULT_MAX_PARTICIPANTS = 6;
    public static final int MIN_CONFIGURABLE_PARTICIPANTS = 1;
    public static final int MAX_CONFIGURABLE_PARTICIPANTS = 10;

    public static InfiniteCanvasParticipantLimit defaultLimit() {
        return new InfiniteCanvasParticipantLimit(DEFAULT_MIN_PARTICIPANTS, DEFAULT_MAX_PARTICIPANTS);
    }

    public static InfiniteCanvasParticipantLimit fromJson(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new InvalidInfiniteCanvasParticipantLimitException("무한 캔버스 참여 인원 제한 설정은 JSON 객체여야 합니다.");
        }

        int minParticipants = requireInteger(value.get("min"), "min");
        int maxParticipants = requireInteger(value.get("max"), "max");
        validate(minParticipants, maxParticipants);

        return new InfiniteCanvasParticipantLimit(minParticipants, maxParticipants);
    }

    private static int requireInteger(JsonNode value, String fieldName) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidInfiniteCanvasParticipantLimitException("무한 캔버스 참여 인원 제한 설정 필드는 정수여야 합니다: " + fieldName);
        }

        return value.asInt();
    }

    private static void validate(int minParticipants, int maxParticipants) {
        if (minParticipants < MIN_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidInfiniteCanvasParticipantLimitException("무한 캔버스 최소 참여 인원은 1명 이상이어야 합니다.");
        }

        if (maxParticipants < minParticipants) {
            throw new InvalidInfiniteCanvasParticipantLimitException("무한 캔버스 최대 참여 인원은 최소 참여 인원 이상이어야 합니다.");
        }

        if (maxParticipants > MAX_CONFIGURABLE_PARTICIPANTS) {
            throw new InvalidInfiniteCanvasParticipantLimitException("무한 캔버스 최대 참여 인원이 설정 가능한 상한을 초과했습니다.");
        }
    }
}
