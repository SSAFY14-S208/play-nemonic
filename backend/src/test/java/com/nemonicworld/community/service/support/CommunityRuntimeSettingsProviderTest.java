package com.nemonicworld.community.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityRuntimeSettingsProviderTest {

    private final SystemParameterRepository systemParameterRepository = mock(SystemParameterRepository.class);
    private final CommunityRuntimeSettingsProvider provider = new CommunityRuntimeSettingsProvider(
        systemParameterRepository, new ObjectMapper());

    @Test
    void currentMaxVisibleMemoCountReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY,
                "{\"value\":30,\"unit\":\"count\"}")));

        assertThat(provider.currentMaxVisibleMemoCount()).isEqualTo(30);
    }

    @Test
    void currentMaxVisibleMemoCountFallsBackWhenSettingMissingOrBlank() {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY))
            .willReturn(Optional.empty())
            .willReturn(Optional.of(systemParameter(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY, " ")));

        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":\"50\"}", "{\"value\":0}", "[]"})
    void currentMaxVisibleMemoCountFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY))
            .willReturn(Optional
                .of(systemParameter(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY, settingValue)));

        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
    }

    @Test
    void currentReportHideThresholdReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY,
                "{\"value\":3,\"unit\":\"count\"}")));

        assertThat(provider.currentReportHideThreshold()).isEqualTo(3);
    }

    @Test
    void currentReportHideThresholdFallsBackWhenSettingMissingOrBlank() {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY))
            .willReturn(Optional.empty()).willReturn(
                Optional.of(systemParameter(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY, " ")));

        assertThat(provider.currentReportHideThreshold())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_REPORT_HIDE_THRESHOLD);
        assertThat(provider.currentReportHideThreshold())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_REPORT_HIDE_THRESHOLD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":\"5\"}", "{\"value\":0}", "[]"})
    void currentReportHideThresholdFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY))
            .willReturn(Optional
                .of(systemParameter(CommunityRuntimeSettingsProvider.REPORT_HIDE_THRESHOLD_SETTING_KEY, settingValue)));

        assertThat(provider.currentReportHideThreshold())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_REPORT_HIDE_THRESHOLD);
    }

    private SystemParameter systemParameter(String key, String value) {
        LocalDateTime now = LocalDateTime.now();

        return new SystemParameter(1L, key, value, 0L, null, now, now);
    }
}
