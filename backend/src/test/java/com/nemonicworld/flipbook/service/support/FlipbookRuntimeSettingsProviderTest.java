package com.nemonicworld.flipbook.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlipbookRuntimeSettingsProviderTest {

    private final SystemParameterRepository systemParameterRepository = mock(SystemParameterRepository.class);
    private final FlipbookRuntimeSettingsProvider provider = new FlipbookRuntimeSettingsProvider(
        systemParameterRepository, new ObjectMapper());

    @Test
    void currentParticipantLimitReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY,
                "{\"min\":3,\"max\":8,\"unit\":\"people\"}")));

        FlipbookRoomParticipantLimit participantLimit = provider.currentParticipantLimit();

        assertThat(participantLimit.minParticipants()).isEqualTo(3);
        assertThat(participantLimit.maxParticipants()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"min\":1,\"max\":6}", "{\"min\":5,\"max\":4}",
        "{\"min\":\"2\",\"max\":6}", "{\"min\":2}", "{\"min\":2,\"max\":\"6\"}", "{\"min\":2,\"max\":21}", "[2,6]"})
    void currentParticipantLimitFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY))
            .willReturn(Optional
                .of(systemParameter(FlipbookRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY, settingValue)));

        assertThat(provider.currentParticipantLimit()).isEqualTo(FlipbookRoomParticipantLimit.defaultLimit());
    }

    @Test
    void currentRoomTimeLimitSettingsReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                "{\"default\":60,\"allowed\":[45,60,90],\"unit\":\"seconds\"}")));

        FlipbookRoomTimeLimitSettings settings = provider.currentRoomTimeLimitSettings();

        assertThat(settings.defaultSeconds()).isEqualTo(60);
        assertThat(settings.allowedSeconds()).containsExactly(45, 60, 90);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"default\":45}", "{\"allowed\":[30,45,60]}",
        "{\"default\":45,\"allowed\":[]}", "{\"default\":45,\"allowed\":[30,60]}",
        "{\"default\":\"45\",\"allowed\":[30,45,60]}", "{\"default\":45,\"allowed\":[30,\"45\",60]}",
        "{\"default\":4,\"allowed\":[4,45,60]}", "{\"default\":601,\"allowed\":[45,601]}", "[]"})
    void currentRoomTimeLimitSettingsFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(
                systemParameter(FlipbookRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, settingValue)));

        assertThat(provider.currentRoomTimeLimitSettings()).isEqualTo(FlipbookRoomTimeLimitSettings.defaultSettings());
    }

    @Test
    void currentMinFramesPerFlipbookReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY,
                "{\"value\":10,\"unit\":\"frames\"}")));

        assertThat(provider.currentMinFramesPerFlipbook()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":0}", "{\"value\":-1}", "{\"value\":\"8\"}", "[]"})
    void currentMinFramesPerFlipbookFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY))
            .willReturn(Optional.of(
                systemParameter(FlipbookRuntimeSettingsProvider.MIN_FRAMES_PER_FLIPBOOK_SETTING_KEY, settingValue)));

        assertThat(provider.currentMinFramesPerFlipbook())
            .isEqualTo(FlipbookMinFramesPerFlipbookSettings.DEFAULT_MIN_FRAMES_PER_FLIPBOOK);
    }

    @Test
    void currentReconnectGracePeriodReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY,
                "{\"value\":30,\"unit\":\"seconds\"}")));

        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":\"10\"}", "{\"value\":-1}", "{\"value\":301}", "[]"})
    void currentReconnectGracePeriodFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(
                systemParameter(FlipbookRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY, settingValue)));

        assertThat(provider.currentReconnectGracePeriod())
            .isEqualTo(Duration.ofSeconds(FlipbookReconnectGraceSettings.DEFAULT_RECONNECT_GRACE_SECONDS));
    }

    private SystemParameter systemParameter(String key, String value) {
        LocalDateTime now = LocalDateTime.now();

        return new SystemParameter(1L, key, value, 0L, null, now, now);
    }
}
