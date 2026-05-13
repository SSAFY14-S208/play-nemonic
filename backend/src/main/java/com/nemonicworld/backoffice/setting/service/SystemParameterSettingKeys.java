package com.nemonicworld.backoffice.setting.service;

import com.nemonicworld.community.service.support.CommunityRuntimeSettingsProvider;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;

final class SystemParameterSettingKeys {

    static final String COMMUNITY_MAX_MEMO_COUNT = CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY;
    static final String COMMUNITY_REPORT_HIDE_THRESHOLD = CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY;
    static final String RELAY_ROOM_PARTICIPANT_LIMIT = RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY;
    static final String RELAY_ROOM_TIME_LIMIT_SECONDS = RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY;
    static final String RELAY_RECONNECT_GRACE_SECONDS = RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY;
    static final String FLIPBOOK_ROOM_PARTICIPANT_LIMIT = FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY;
    static final String FLIPBOOK_ROOM_TIME_LIMIT_SECONDS = FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY;
    static final String FLIPBOOK_MIN_FRAMES_PER_FLIPBOOK = FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY;
    static final String FLIPBOOK_RECONNECT_GRACE_SECONDS = FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY;
    static final String FORTUNE_DAILY_LIMIT = "fortune.daily_limit";
    static final String CS_INQUIRY_UNRESOLVED_ALERT_THRESHOLD_HOURS = "cs_inquiry.unresolved_alert_threshold_hours";

    private SystemParameterSettingKeys() {
    }
}
