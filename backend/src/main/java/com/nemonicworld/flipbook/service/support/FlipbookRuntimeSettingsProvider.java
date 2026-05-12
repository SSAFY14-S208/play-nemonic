package com.nemonicworld.flipbook.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.time.Duration;
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
        return systemParameterRepository.findByKey(PARTICIPANT_LIMIT_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseParticipantLimit).orElseGet(() -> {
                FlipbookRoomParticipantLimit fallback = FlipbookRoomParticipantLimit.defaultLimit();
                log.warn("flipbook participant limit setting is missing or blank. key={} fallbackMin={} fallbackMax={}",
                    PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants());

                return fallback;
            });
    }

    public FlipbookRoomTimeLimitSettings currentRoomTimeLimitSettings() {
        return systemParameterRepository.findByKey(ROOM_TIME_LIMIT_SECONDS_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseRoomTimeLimitSettings).orElseGet(() -> {
                FlipbookRoomTimeLimitSettings fallback = FlipbookRoomTimeLimitSettings.defaultSettings();
                log.warn(
                    "flipbook room time limit setting is missing or blank. key={} fallbackDefault={} "
                        + "fallbackAllowed={}",
                    ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds());

                return fallback;
            });
    }

    public int currentMinFramesPerFlipbook() {
        return systemParameterRepository.findByKey(MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseMinFramesPerFlipbook).orElseGet(() -> {
                FlipbookMinFramesPerFlipbookSettings fallback = FlipbookMinFramesPerFlipbookSettings.defaultSettings();
                log.warn("flipbook min frames setting is missing or blank. key={} fallbackValue={}",
                    MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, fallback.value());

                return fallback.value();
            });
    }

    public Duration currentReconnectGracePeriod() {
        return systemParameterRepository.findByKey(RECONNECT_GRACE_SECONDS_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseReconnectGracePeriod).orElseGet(() -> {
                FlipbookReconnectGraceSettings fallback = FlipbookReconnectGraceSettings.defaultSettings();
                log.warn("flipbook reconnect grace setting is missing or blank. key={} fallbackSeconds={}",
                    RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds());

                return fallback.period();
            });
    }

    private FlipbookRoomParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return FlipbookRoomParticipantLimit.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | InvalidFlipbookRoomParticipantLimitException e) {
            FlipbookRoomParticipantLimit fallback = FlipbookRoomParticipantLimit.defaultLimit();
            log.warn("flipbook participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }

    private FlipbookRoomTimeLimitSettings parseRoomTimeLimitSettings(String settingValue) {
        try {
            return FlipbookRoomTimeLimitSettings.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | InvalidFlipbookRoomTimeLimitSettingsException e) {
            FlipbookRoomTimeLimitSettings fallback = FlipbookRoomTimeLimitSettings.defaultSettings();
            log.warn("flipbook room time limit setting is invalid. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds(), e);

            return fallback;
        }
    }

    private int parseMinFramesPerFlipbook(String settingValue) {
        try {
            return FlipbookMinFramesPerFlipbookSettings.fromJson(objectMapper.readTree(settingValue)).value();
        } catch (JsonProcessingException | InvalidFlipbookMinFramesPerFlipbookSettingsException e) {
            FlipbookMinFramesPerFlipbookSettings fallback = FlipbookMinFramesPerFlipbookSettings.defaultSettings();
            log.warn("flipbook min frames setting is invalid. key={} fallbackValue={}",
                MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, fallback.value(), e);

            return fallback.value();
        }
    }

    private Duration parseReconnectGracePeriod(String settingValue) {
        try {
            return FlipbookReconnectGraceSettings.fromJson(objectMapper.readTree(settingValue)).period();
        } catch (JsonProcessingException | InvalidFlipbookReconnectGraceSettingsException e) {
            FlipbookReconnectGraceSettings fallback = FlipbookReconnectGraceSettings.defaultSettings();
            log.warn("flipbook reconnect grace setting is invalid. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds(), e);

            return fallback.period();
        }
    }
}
