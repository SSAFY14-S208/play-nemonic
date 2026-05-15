package com.nemonicworld.infinitecanvas.service.support;

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

class InfiniteCanvasRuntimeSettingsProviderTest {

    private final SystemParameterRepository systemParameterRepository = mock(SystemParameterRepository.class);
    private final InfiniteCanvasRuntimeSettingsProvider provider = new InfiniteCanvasRuntimeSettingsProvider(
        systemParameterRepository, new ObjectMapper());

    @Test
    void currentParticipantLimitReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter("{\"min\":2,\"max\":8,\"unit\":\"people\"}")));

        InfiniteCanvasParticipantLimit participantLimit = provider.currentParticipantLimit();

        assertThat(participantLimit.minParticipants()).isEqualTo(2);
        assertThat(participantLimit.maxParticipants()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"min\":0,\"max\":6}", "{\"min\":5,\"max\":4}",
        "{\"min\":\"2\",\"max\":6}", "{\"min\":2}", "{\"min\":2,\"max\":\"6\"}", "{\"min\":2,\"max\":11}", "[2,6]"})
    void currentParticipantLimitFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(settingValue)));

        assertThat(provider.currentParticipantLimit()).isEqualTo(InfiniteCanvasParticipantLimit.defaultLimit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void currentParticipantLimitFallsBackWhenSettingBlank(String settingValue) {
        given(systemParameterRepository.findByKey(InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(settingValue)));

        assertThat(provider.currentParticipantLimit()).isEqualTo(InfiniteCanvasParticipantLimit.defaultLimit());
    }

    @Test
    void currentParticipantLimitFallsBackWhenSettingMissing() {
        given(systemParameterRepository.findByKey(InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional.empty());

        assertThat(provider.currentParticipantLimit()).isEqualTo(InfiniteCanvasParticipantLimit.defaultLimit());
    }

    private SystemParameter systemParameter(String value) {
        LocalDateTime now = LocalDateTime.now();

        return new SystemParameter(1L, InfiniteCanvasRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY, value, 0L,
            null, now, now);
    }
}
