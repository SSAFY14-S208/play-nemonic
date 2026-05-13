package com.nemonicworld.relay.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        return resolveParticipantLimit(systemParameterRepository.findByKey(PARTICIPANT_LIMIT_SETTING_KEY)
            .map(SystemParameter::value).orElse(null));
    }

    public RelayRoomTimeLimitSettings currentRoomTimeLimitSettings() {
        return resolveRoomTimeLimitSettings(systemParameterRepository.findByKey(ROOM_TIME_LIMIT_SECONDS_SETTING_KEY)
            .map(SystemParameter::value).orElse(null));
    }

    public Duration currentReconnectGracePeriod() {
        return resolveReconnectGracePeriod(systemParameterRepository.findByKey(RECONNECT_GRACE_SECONDS_SETTING_KEY)
            .map(SystemParameter::value).orElse(null));
    }

    public RelayRuntimeSettingsSnapshot currentSettingsSnapshot() {
        Map<String, SystemParameter> parametersByKey = systemParameterRepository
            .findAllByKeys(List.of(PARTICIPANT_LIMIT_SETTING_KEY, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .stream().collect(Collectors.toMap(SystemParameter::key, Function.identity(), (first, second) -> first));

        return new RelayRuntimeSettingsSnapshot(
            resolveParticipantLimit(valueOf(parametersByKey, PARTICIPANT_LIMIT_SETTING_KEY)),
            resolveRoomTimeLimitSettings(valueOf(parametersByKey, ROOM_TIME_LIMIT_SECONDS_SETTING_KEY)),
            resolveReconnectGracePeriod(valueOf(parametersByKey, RECONNECT_GRACE_SECONDS_SETTING_KEY)));
    }

    private String valueOf(Map<String, SystemParameter> parametersByKey, String key) {
        SystemParameter parameter = parametersByKey.get(key);

        return parameter == null ? null : parameter.value();
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
