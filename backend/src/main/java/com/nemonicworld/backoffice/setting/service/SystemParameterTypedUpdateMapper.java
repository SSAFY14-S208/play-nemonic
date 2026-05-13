package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import java.util.ArrayList;
import java.util.List;

final class SystemParameterTypedUpdateMapper {

    private SystemParameterTypedUpdateMapper() {
    }

    static List<TypedUpdateValue> extractUpdates(SystemParameterTypedUpdateRequest request, ObjectMapper objectMapper) {
        List<TypedUpdateValue> updates = new ArrayList<>();
        if (request == null) {
            return updates;
        }

        add(updates, objectMapper, "community.max_memo_count", request.communityMaxMemoCount());
        add(updates, objectMapper, "community.report_hide_threshold", request.communityReportHideThreshold());
        add(updates, objectMapper, "relay.room_participant_limit", request.relayRoomParticipantLimit());
        add(updates, objectMapper, "relay.room_time_limit_seconds", request.relayRoomTimeLimitSeconds());
        add(updates, objectMapper, "relay.reconnect_grace_seconds", request.relayReconnectGraceSeconds());
        add(updates, objectMapper, "flipbook.room_participant_limit", request.flipbookRoomParticipantLimit());
        add(updates, objectMapper, "flipbook.room_time_limit_seconds", request.flipbookRoomTimeLimitSeconds());
        add(updates, objectMapper, "flipbook.min_frames_per_flipbook", request.flipbookMinFramesPerFlipbook());
        add(updates, objectMapper, "flipbook.reconnect_grace_seconds", request.flipbookReconnectGraceSeconds());
        add(updates, objectMapper, "fortune.daily_limit", request.fortuneDailyLimit());
        add(updates, objectMapper, "cs_inquiry.unresolved_alert_threshold_hours",
            request.csInquiryUnresolvedAlertThresholdHours());

        return updates;
    }

    private static void add(List<TypedUpdateValue> updates, ObjectMapper objectMapper, String key, Object value) {
        if (value != null) {
            updates.add(new TypedUpdateValue(key, objectMapper.valueToTree(value)));
        }
    }

    record TypedUpdateValue(String key, JsonNode value) {
    }
}
