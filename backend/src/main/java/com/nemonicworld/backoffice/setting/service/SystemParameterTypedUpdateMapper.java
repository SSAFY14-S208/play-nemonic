package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import java.util.ArrayList;
import java.util.List;

public final class SystemParameterTypedUpdateMapper {

    private SystemParameterTypedUpdateMapper() {
    }

    public static List<TypedUpdateValue> extractUpdates(SystemParameterTypedUpdateRequest request,
        ObjectMapper objectMapper) {
        List<TypedUpdateValue> updates = new ArrayList<>();
        if (request == null) {
            return updates;
        }

        add(updates, objectMapper, SystemParameterSettingKeys.COMMUNITY_MAX_MEMO_COUNT,
            request.communityMaxMemoCount());
        add(updates, objectMapper, SystemParameterSettingKeys.COMMUNITY_REPORT_HIDE_THRESHOLD,
            request.communityReportHideThreshold());
        add(updates, objectMapper, SystemParameterSettingKeys.RELAY_ROOM_PARTICIPANT_LIMIT,
            request.relayRoomParticipantLimit());
        add(updates, objectMapper, SystemParameterSettingKeys.RELAY_ROOM_TIME_LIMIT_SECONDS,
            request.relayRoomTimeLimitSeconds());
        add(updates, objectMapper, SystemParameterSettingKeys.RELAY_RECONNECT_GRACE_SECONDS,
            request.relayReconnectGraceSeconds());
        add(updates, objectMapper, SystemParameterSettingKeys.FLIPBOOK_ROOM_PARTICIPANT_LIMIT,
            request.flipbookRoomParticipantLimit());
        add(updates, objectMapper, SystemParameterSettingKeys.FLIPBOOK_ROOM_TIME_LIMIT_SECONDS,
            request.flipbookRoomTimeLimitSeconds());
        add(updates, objectMapper, SystemParameterSettingKeys.FLIPBOOK_MIN_FRAMES_PER_FLIPBOOK,
            request.flipbookMinFramesPerFlipbook());
        add(updates, objectMapper, SystemParameterSettingKeys.FLIPBOOK_RECONNECT_GRACE_SECONDS,
            request.flipbookReconnectGraceSeconds());
        add(updates, objectMapper, SystemParameterSettingKeys.INFINITE_CANVAS_PARTICIPANT_LIMIT,
            request.infiniteCanvasParticipantLimit());
        add(updates, objectMapper, SystemParameterSettingKeys.FORTUNE_DAILY_LIMIT, request.fortuneDailyLimit());
        add(updates, objectMapper, SystemParameterSettingKeys.CS_INQUIRY_UNRESOLVED_ALERT_THRESHOLD_HOURS,
            request.csInquiryUnresolvedAlertThresholdHours());

        return updates;
    }

    private static void add(List<TypedUpdateValue> updates, ObjectMapper objectMapper, String key, Object value) {
        if (value != null) {
            updates.add(new TypedUpdateValue(key, objectMapper.valueToTree(value)));
        }
    }

    public record TypedUpdateValue(String key, JsonNode value) {
    }
}
