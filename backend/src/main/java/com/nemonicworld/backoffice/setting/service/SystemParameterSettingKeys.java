package com.nemonicworld.backoffice.setting.service;

import com.nemonicworld.community.service.support.CommunityRuntimeSettingsProvider;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasRuntimeSettingsProvider;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;

final class SystemParameterSettingKeys {

    static final String COMMUNITY_MAX_MEMO_COUNT = CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY;
    static final String COMMUNITY_REPORT_HIDE_THRESHOLD = communityReportHideThreshold();
    static final String RELAY_ROOM_PARTICIPANT_LIMIT = RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY;
    static final String RELAY_ROOM_TIME_LIMIT_SECONDS = relayRoomTimeLimitSeconds();
    static final String RELAY_RECONNECT_GRACE_SECONDS = relayReconnectGraceSeconds();
    static final String FLIPBOOK_ROOM_PARTICIPANT_LIMIT = FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY;
    static final String FLIPBOOK_ROOM_TIME_LIMIT_SECONDS = flipbookRoomTimeLimitSeconds();
    static final String FLIPBOOK_MIN_FRAMES_PER_FLIPBOOK = flipbookMinFramesPerFlipbook();
    static final String FLIPBOOK_RECONNECT_GRACE_SECONDS = flipbookReconnectGraceSeconds();
    static final String INFINITE_CANVAS_PARTICIPANT_LIMIT = infiniteCanvasParticipantLimit();
    static final String FORTUNE_DAILY_LIMIT = "fortune.daily_limit";
    static final String CS_INQUIRY_UNRESOLVED_ALERT_THRESHOLD_HOURS = "cs_inquiry.unresolved_alert_threshold_hours";

    private SystemParameterSettingKeys() {
    }

    private static String communityReportHideThreshold() {
        return CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY;
    }

    private static String relayRoomTimeLimitSeconds() {
        return RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY;
    }

    private static String relayReconnectGraceSeconds() {
        return RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY;
    }

    private static String flipbookRoomTimeLimitSeconds() {
        return FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY;
    }

    private static String flipbookMinFramesPerFlipbook() {
        return FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY;
    }

    private static String flipbookReconnectGraceSeconds() {
        return FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY;
    }

    private static String infiniteCanvasParticipantLimit() {
        return InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY;
    }
}
