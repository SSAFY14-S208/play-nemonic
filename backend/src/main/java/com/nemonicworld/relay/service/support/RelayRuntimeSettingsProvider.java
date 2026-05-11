package com.nemonicworld.relay.service.support;

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
        return systemParameterRepository.findByKey(PARTICIPANT_LIMIT_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseParticipantLimit).orElseGet(() -> {
                RelayRoomParticipantLimit fallback = RelayRoomParticipantLimit.defaultLimit();
                log.warn("relay participant limit setting is missing or blank. key={} fallbackMin={} fallbackMax={}",
                    PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants());

                return fallback;
            });
    }

    public RelayRoomTimeLimitSettings currentRoomTimeLimitSettings() {
        return systemParameterRepository.findByKey(ROOM_TIME_LIMIT_SECONDS_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseRoomTimeLimitSettings).orElseGet(() -> {
                RelayRoomTimeLimitSettings fallback = RelayRoomTimeLimitSettings.defaultSettings();
                log.warn(
                    "relay room time limit setting is missing or blank. key={} fallbackDefault={} fallbackAllowed={}",
                    ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds());

                return fallback;
            });
    }

    public Duration currentReconnectGracePeriod() {
        return systemParameterRepository.findByKey(RECONNECT_GRACE_SECONDS_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseReconnectGracePeriod).orElseGet(() -> {
                RelayReconnectGraceSettings fallback = RelayReconnectGraceSettings.defaultSettings();
                log.warn("relay reconnect grace setting is missing or blank. key={} fallbackSeconds={}",
                    RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds());

                return fallback.period();
            });
    }

    private RelayRoomParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return RelayRoomParticipantLimit.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | InvalidRelayRoomParticipantLimitException e) {
            RelayRoomParticipantLimit fallback = RelayRoomParticipantLimit.defaultLimit();
            log.warn("relay participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }

    private RelayRoomTimeLimitSettings parseRoomTimeLimitSettings(String settingValue) {
        try {
            return RelayRoomTimeLimitSettings.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | InvalidRelayRoomTimeLimitSettingsException e) {
            RelayRoomTimeLimitSettings fallback = RelayRoomTimeLimitSettings.defaultSettings();
            log.warn("relay room time limit setting is invalid. key={} fallbackDefault={} fallbackAllowed={}",
                ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, fallback.defaultSeconds(), fallback.allowedSeconds(), e);

            return fallback;
        }
    }

    private Duration parseReconnectGracePeriod(String settingValue) {
        try {
            return RelayReconnectGraceSettings.fromJson(objectMapper.readTree(settingValue)).period();
        } catch (JsonProcessingException | InvalidRelayReconnectGraceSettingsException e) {
            RelayReconnectGraceSettings fallback = RelayReconnectGraceSettings.defaultSettings();
            log.warn("relay reconnect grace setting is invalid. key={} fallbackSeconds={}",
                RECONNECT_GRACE_SECONDS_SETTING_KEY, fallback.seconds(), e);

            return fallback.period();
        }
    }
}
