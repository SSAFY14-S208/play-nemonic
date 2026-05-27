package com.nemonicworld.flipbook.service.support;

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
public class FlipbookRuntimeSettingsProvider {

    public static final String PARTICIPANT_LIMIT_SETTING_KEY = "flipbook.room_participant_limit";
    public static final String ROOM_TIME_LIMIT_SECONDS_SETTING_KEY = "flipbook.room_time_limit_seconds";
    public static final String MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY = "flipbook.min_frames_per_flipbook";
    public static final String RECONNECT_GRACE_SECONDS_SETTING_KEY = "flipbook.reconnect_grace_seconds";

    private static final Logger log = LoggerFactory.getLogger(FlipbookRuntimeSettingsProvider.class);

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public FlipbookRuntimeSettingsProvider(SystemParameterRepository systemParameterRepository,
        ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    public FlipbookRoomParticipantLimit currentParticipantLimit() {
        return resolveParticipantLimit(SystemParameterValueResolver
            .findValue(systemParameterRepository, PARTICIPANT_LIMIT_SETTING_KEY).orElse(null));
    }

    public FlipbookRoomTimeLimitSettings currentRoomTimeLimitSettings() {
        return resolveRoomTimeLimitSettings(SystemParameterValueResolver
            .findValue(systemParameterRepository, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY).orElse(null));
    }

    public int currentMinFramesPerFlipbook() {
        return resolveMinFramesPerFlipbook(SystemParameterValueResolver
            .findValue(systemParameterRepository, MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY).orElse(null));
    }

    public Duration currentReconnectGracePeriod() {
        return resolveReconnectGracePeriod(SystemParameterValueResolver
            .findValue(systemParameterRepository, RECONNECT_GRACE_SECONDS_SETTING_KEY).orElse(null));
    }

    public FlipbookRuntimeSettingsSnapshot currentSettingsSnapshot() {
        Map<String, String> valuesByKey = SystemParameterValueResolver.findValues(systemParameterRepository,
            List.of(PARTICIPANT_LIMIT_SETTING_KEY, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, RECONNECT_GRACE_SECONDS_SETTING_KEY));

        return new FlipbookRuntimeSettingsSnapshot(
            resolveParticipantLimit(SystemParameterValueResolver.valueOf(valuesByKey, PARTICIPANT_LIMIT_SETTING_KEY)),
            resolveRoomTimeLimitSettings(
                SystemParameterValueResolver.valueOf(valuesByKey, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY)),
            resolveMinFramesPerFlipbook(
                SystemParameterValueResolver.valueOf(valuesByKey, MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY)),
            resolveReconnectGracePeriod(
                SystemParameterValueResolver.valueOf(valuesByKey, RECONNECT_GRACE_SECONDS_SETTING_KEY)));
    }

    private FlipbookRoomParticipantLimit resolveParticipantLimit(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            FlipbookRoomParticipantLimit fallback = FlipbookRoomParticipantLimit.defaultLimit();
            log.warn("flipbook participant limit setting is missing or blank. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants());

            return fallback;
        }

        return parseParticipantLimit(settingValue);
    }

    private FlipbookRoomTimeLimitSettings resolveRoomTimeLimitSettings(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            FlipbookRoomTimeLimitSettings fallback = FlipbookRoomTimeLimitSettings.defaultSettings();
            log.warn(
                "flipbook room time limit setting is missing or blank. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds());

            return fallback;
        }

        return parseRoomTimeLimitSettings(settingValue);
    }

    private int resolveMinFramesPerFlipbook(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            FlipbookMinFramesPerFlipbookSettings fallback = FlipbookMinFramesPerFlipbookSettings.defaultSettings();
            log.warn("flipbook min frames setting is missing or blank. key={} fallbackValue={}",
                MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, fallback.value());

            return fallback.value();
        }

        return parseMinFramesPerFlipbook(settingValue);
    }

    private Duration resolveReconnectGracePeriod(String settingValue) {
        if (!StringUtils.hasText(settingValue)) {
            FlipbookReconnectGraceSettings fallback = FlipbookReconnectGraceSettings.defaultSettings();
            log.warn("flipbook reconnect grace setting is missing or blank. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds());

            return fallback.period();
        }

        return parseReconnectGracePeriod(settingValue);
    }

    private FlipbookRoomParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return FlipbookRoomParticipantLimit
                .fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue));
        } catch (JsonProcessingException | InvalidFlipbookRoomParticipantLimitException e) {
            FlipbookRoomParticipantLimit fallback = FlipbookRoomParticipantLimit.defaultLimit();
            log.warn("flipbook participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }

    private FlipbookRoomTimeLimitSettings parseRoomTimeLimitSettings(String settingValue) {
        try {
            return FlipbookRoomTimeLimitSettings
                .fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue));
        } catch (JsonProcessingException | InvalidFlipbookRoomTimeLimitSettingsException e) {
            FlipbookRoomTimeLimitSettings fallback = FlipbookRoomTimeLimitSettings.defaultSettings();
            log.warn("flipbook room time limit setting is invalid. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds(), e);

            return fallback;
        }
    }

    private int parseMinFramesPerFlipbook(String settingValue) {
        try {
            return FlipbookMinFramesPerFlipbookSettings
                .fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue)).value();
        } catch (JsonProcessingException | InvalidFlipbookMinFramesPerFlipbookSettingsException e) {
            FlipbookMinFramesPerFlipbookSettings fallback = FlipbookMinFramesPerFlipbookSettings.defaultSettings();
            log.warn("flipbook min frames setting is invalid. key={} fallbackValue={}",
                MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, fallback.value(), e);

            return fallback.value();
        }
    }

    private Duration parseReconnectGracePeriod(String settingValue) {
        try {
            return FlipbookReconnectGraceSettings
                .fromJson(SystemParameterJsonReader.readTree(objectMapper, settingValue)).period();
        } catch (JsonProcessingException | InvalidFlipbookReconnectGraceSettingsException e) {
            FlipbookReconnectGraceSettings fallback = FlipbookReconnectGraceSettings.defaultSettings();
            log.warn("flipbook reconnect grace setting is invalid. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds(), e);

            return fallback.period();
        }
    }
}
