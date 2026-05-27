package com.nemonicworld.community.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.service.SystemParameterJsonReader;
import com.nemonicworld.backoffice.setting.service.SystemParameterValueResolver;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommunityRuntimeSettingsProvider {

    public static final String MAX_MEMO_COUNT_SETTING_KEY = "community.max_memo_count";
    public static final String REPORT_HIDE_THRESHOLD_SETTING_KEY = "community.report_hide_threshold";
    public static final int DEFAULT_MAX_VISIBLE_MEMO_COUNT = 50;
    public static final int DEFAULT_REPORT_HIDE_THRESHOLD = 5;

    private static final Logger log = LoggerFactory.getLogger(CommunityRuntimeSettingsProvider.class);

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public CommunityRuntimeSettingsProvider(SystemParameterRepository systemParameterRepository,
        ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    public int currentMaxVisibleMemoCount() {
        return SystemParameterValueResolver.findValue(systemParameterRepository, MAX_MEMO_COUNT_SETTING_KEY)
            .filter(StringUtils::hasText)
            .map(
                value -> parsePositiveIntegerSetting(MAX_MEMO_COUNT_SETTING_KEY, value, DEFAULT_MAX_VISIBLE_MEMO_COUNT))
            .orElseGet(() -> {
                log.warn("community max memo count setting is missing or blank. key={} fallbackValue={}",
                    MAX_MEMO_COUNT_SETTING_KEY, DEFAULT_MAX_VISIBLE_MEMO_COUNT);

                return DEFAULT_MAX_VISIBLE_MEMO_COUNT;
            });
    }

    public int currentReportHideThreshold() {
        return SystemParameterValueResolver.findValue(systemParameterRepository, REPORT_HIDE_THRESHOLD_SETTING_KEY)
            .filter(StringUtils::hasText).map(value -> parsePositiveIntegerSetting(REPORT_HIDE_THRESHOLD_SETTING_KEY,
                value, DEFAULT_REPORT_HIDE_THRESHOLD))
            .orElseGet(() -> {
                log.warn("community report hide threshold setting is missing or blank. key={} fallbackValue={}",
                    REPORT_HIDE_THRESHOLD_SETTING_KEY, DEFAULT_REPORT_HIDE_THRESHOLD);

                return DEFAULT_REPORT_HIDE_THRESHOLD;
            });
    }

    private int parsePositiveIntegerSetting(String key, String settingValue, int fallbackValue) {
        try {
            Optional<Integer> value = SystemParameterJsonReader.readPositiveIntegerValue(objectMapper, settingValue);
            if (value.isEmpty()) {
                log.warn("community positive integer setting is invalid. key={} fallbackValue={} reason=invalid_value",
                    key, fallbackValue);

                return fallbackValue;
            }

            return value.get();
        } catch (JsonProcessingException e) {
            log.warn("community positive integer setting is invalid. key={} fallbackValue={} reason=invalid_json", key,
                fallbackValue, e);

            return fallbackValue;
        }
    }
}
