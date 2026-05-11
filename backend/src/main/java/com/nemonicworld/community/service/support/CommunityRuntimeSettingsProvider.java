package com.nemonicworld.community.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommunityRuntimeSettingsProvider {

    public static final String MAX_MEMO_COUNT_SETTING_KEY = "community.max_memo_count";
    public static final int DEFAULT_MAX_VISIBLE_MEMO_COUNT = 50;

    private static final Logger log = LoggerFactory.getLogger(CommunityRuntimeSettingsProvider.class);

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public CommunityRuntimeSettingsProvider(SystemParameterRepository systemParameterRepository,
        ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    public int currentMaxVisibleMemoCount() {
        return systemParameterRepository.findByKey(MAX_MEMO_COUNT_SETTING_KEY).map(SystemParameter::value)
            .filter(StringUtils::hasText).map(this::parseMaxVisibleMemoCount).orElseGet(() -> {
                log.warn("community max memo count setting is missing or blank. key={} fallbackValue={}",
                    MAX_MEMO_COUNT_SETTING_KEY, DEFAULT_MAX_VISIBLE_MEMO_COUNT);

                return DEFAULT_MAX_VISIBLE_MEMO_COUNT;
            });
    }

    private int parseMaxVisibleMemoCount(String settingValue) {
        try {
            JsonNode root = objectMapper.readTree(settingValue);
            JsonNode value = root == null || !root.isObject() ? null : root.get("value");
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
                log.warn("community max memo count setting is invalid. key={} fallbackValue={} reason=invalid_value",
                    MAX_MEMO_COUNT_SETTING_KEY, DEFAULT_MAX_VISIBLE_MEMO_COUNT);

                return DEFAULT_MAX_VISIBLE_MEMO_COUNT;
            }

            return value.asInt();
        } catch (JsonProcessingException e) {
            log.warn("community max memo count setting is invalid. key={} fallbackValue={} reason=invalid_json",
                MAX_MEMO_COUNT_SETTING_KEY, DEFAULT_MAX_VISIBLE_MEMO_COUNT, e);

            return DEFAULT_MAX_VISIBLE_MEMO_COUNT;
        }
    }
}
