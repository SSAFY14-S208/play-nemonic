package com.nemonicworld.relay.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.service.SystemParameterJsonReader;
import com.nemonicworld.backoffice.setting.service.SystemParameterValueResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayRuntimeSettingsProvider {

    public static final String PARTICIPANT_LIMIT_SETTING_KEY = "relay.room_participant_limit";
    public static final String ROOM_TIME_LIMIT_SECONDS_SETTING_KEY = "relay.room_time_limit_seconds";
    public static final String RECONNECT_GRACE_SECONDS_SETTING_KEY = "relay.reconnect_grace_seconds";

    private static final Logger log = LoggerFactory.getLogger(RelayRuntimeSettingsProvider.class);

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public RelayRuntimeSettingsProvider(SystemParameterRepository systemParameterRepository,
        ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    public RelayRoomParticipantLimit currentParticipantLimit() {
        return resolveParticipantLimit(SystemParameterValueResolver
            .findValue(systemParameterRepository, PARTICIPANT_LIMIT_SETTING_KEY).orElse(null));
    }

    public RelayRoomTimeLimitSettings currentRoomTimeLimitSettings() {
        return resolveRoomTimeLimitSettings(SystemParameterValueResolver
            .findValue(systemParameterRepository, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY).orElse(null));
    }

    public Duration currentReconnectGracePeriod() {
        return resolveReconnectGracePeriod(SystemParameterValueResolver
            .findValue(systemParameterRepository, RECONNECT_GRACE_SECONDS_SETTING_KEY).orElse(null));
    }

    public RelayRuntimeSettingsSnapshot currentSettingsSnapshot() {
        Map<String, String> valuesByKey = SystemParameterValueResolver.findValues(systemParameterRepository, List.of(
            PARTICIPANT_LIMIT_SETTING_KEY, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, RECONNECT_GRACE_SECONDS_SETTING_KEY));

        return new RelayRuntimeSettingsSnapshot(
            resolveParticipantLimit(SystemParameterValueResolver.valueOf(valuesByKey, PARTICIPANT_LIMIT_SETTING_KEY)),
            resolveRoomTimeLimitSettings(
                SystemParameterValueResolver.valueOf(valuesByKey, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY)),
            resolveReconnectGracePeriod(
                SystemParameterValueResolver.valueOf(valuesByKey, RECONNECT_GRACE_SECONDS_SETTING_KEY)));
    }

    private RelayRoomParticipantLimit resolveParticipantLimit(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            RelayRoomParticipantLimit fallback = RelayRoomParticipantLimit.defaultLimit();
            log.warn("relay participant limit setting is missing or blank. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants());

            return fallback;
        }

        return parseParticipantLimit(settingValue);
    }

    private RelayRoomTimeLimitSettings resolveRoomTimeLimitSettings(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            RelayRoomTimeLimitSettings fallback = RelayRoomTimeLimitSettings.defaultSettings();
            log.warn("relay room time limit setting is missing or blank. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds());

            return fallback;
        }

        return parseRoomTimeLimitSettings(settingValue);
    }

    private Duration resolveReconnectGracePeriod(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            RelayReconnectGraceSettings fallback = RelayReconnectGraceSettings.defaultSettings();
            log.warn("relay reconnect grace setting is missing or blank. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds());

            return fallback.period();
        }

        return parseReconnectGracePeriod(settingValue);
    }

    private RelayRoomParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return RelayRoomParticipantLimit.fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue));
        } catch (JsonProcessingException | InvalidRelayRoomParticipantLimitException e) {
            RelayRoomParticipantLimit fallback = RelayRoomParticipantLimit.defaultLimit();
            log.warn("relay participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }

    private RelayRoomTimeLimitSettings parseRoomTimeLimitSettings(String settingValue) {
        try {
            return RelayRoomTimeLimitSettings.fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue));
        } catch (JsonProcessingException | InvalidRelayRoomTimeLimitSettingsException e) {
            RelayRoomTimeLimitSettings fallback = RelayRoomTimeLimitSettings.defaultSettings();
            log.warn("relay room time limit setting is invalid. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds(), e);

            return fallback;
        }
    }

    private Duration parseReconnectGracePeriod(String settingValue) {
        try {
            return RelayReconnectGraceSettings.fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue))
                .period();
        } catch (JsonProcessingException | InvalidRelayReconnectGraceSettingsException e) {
            RelayReconnectGraceSettings fallback = RelayReconnectGraceSettings.defaultSettings();
            log.warn("relay reconnect grace setting is invalid. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds(), e);

            return fallback.period();
        }
    }
}
