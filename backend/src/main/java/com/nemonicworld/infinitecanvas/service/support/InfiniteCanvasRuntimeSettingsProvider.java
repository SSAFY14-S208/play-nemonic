package com.nemonicworld.infinitecanvas.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfiniteCanvasRuntimeSettingsProvider {

    public static final String PARTICIPANT_LIMIT_SETTING_KEY = "infinite_canvas.participant_limit";

    private static final Logger log = LoggerFactory.getLogger(InfiniteCanvasRuntimeSettingsProvider.class);

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public InfiniteCanvasRuntimeSettingsProvider(SystemParameterRepository systemParameterRepository,
        ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    public InfiniteCanvasParticipantLimit currentParticipantLimit() {
        return systemParameterRepository.findByKey(PARTICIPANT_LIMIT_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseParticipantLimit).orElseGet(() -> {
                InfiniteCanvasParticipantLimit fallback = InfiniteCanvasParticipantLimit.defaultLimit();
                log.warn(
                    "infinite canvas participant limit setting is missing or blank. key={} "
                        + "fallbackMin={} fallbackMax={}",
                    PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants());

                return fallback;
            });
    }

    private InfiniteCanvasParticipantLimit parseParticipantLimit(String settingValue) {
        try {
            return InfiniteCanvasParticipantLimit.fromJson(objectMapper.readTree(settingValue));
        } catch (JsonProcessingException | InvalidInfiniteCanvasParticipantLimitException e) {
            InfiniteCanvasParticipantLimit fallback = InfiniteCanvasParticipantLimit.defaultLimit();
            log.warn("infinite canvas participant limit setting is invalid. key={} fallbackMin={} fallbackMax={}",
                PARTICIPANT_LIMIT_SETTING_KEY, fallback.minParticipants(), fallback.maxParticipants(), e);

            return fallback;
        }
    }
}
