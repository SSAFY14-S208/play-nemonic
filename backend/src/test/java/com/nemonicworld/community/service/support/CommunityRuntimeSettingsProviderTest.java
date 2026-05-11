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
            .willReturn(Optional.of(systemParameter("{\"value\":30,\"unit\":\"count\"}")));

        assertThat(provider.currentMaxVisibleMemoCount()).isEqualTo(30);
    }

    @Test
    void currentMaxVisibleMemoCountFallsBackWhenSettingMissingOrBlank() {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY))
            .willReturn(Optional.empty()).willReturn(Optional.of(systemParameter(" ")));

        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":\"50\"}", "{\"value\":0}", "[]"})
    void currentMaxVisibleMemoCountFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(settingValue)));

        assertThat(provider.currentMaxVisibleMemoCount())
            .isEqualTo(CommunityRuntimeSettingsProvider.DEFAULT_MAX_VISIBLE_MEMO_COUNT);
    }

    private SystemParameter systemParameter(String value) {
        LocalDateTime now = LocalDateTime.now();

        return new SystemParameter(1L, CommunityRuntimeSettingsProvider.MAX_MEMO_COUNT_SETTING_KEY, value, 0L, null,
            now, now);
    }
}
