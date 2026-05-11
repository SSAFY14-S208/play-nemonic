package com.nemonicworld.relay.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayRuntimeSettingsProvider {

    public static final String PARTICIPANT_LIMIT_SETTING_KEY = "relay.room_participant_limit";

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

    private RelayRoomParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return RelayRoomParticipantLimit.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            RelayRoomParticipantLimit fallback = RelayRoomParticipantLimit.defaultLimit();
            log.warn("relay participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }
}
