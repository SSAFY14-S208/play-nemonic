package com.nemonicworld.relay.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelayRuntimeSettingsProviderTest {

    private final SystemParameterRepository systemParameterRepository = mock(SystemParameterRepository.class);
    private final RelayRuntimeSettingsProvider provider = new RelayRuntimeSettingsProvider(systemParameterRepository,
        new ObjectMapper());

    @Test
    void currentRoomTimeLimitSettingsReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                "{\"default\":60,\"allowed\":[45,60,90],\"unit\":\"seconds\"}")));

        RelayRoomTimeLimitSettings settings = provider.currentRoomTimeLimitSettings();

        assertThat(settings.defaultSeconds()).isEqualTo(60);
        assertThat(settings.allowedSeconds()).containsExactly(45, 60, 90);
    }

    @Test
    void currentRoomTimeLimitSettingsFallsBackWhenSettingMissingOrBlank() {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY))
            .willReturn(Optional.empty()).willReturn(
                Optional.of(systemParameter(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, " ")));

        assertThat(provider.currentRoomTimeLimitSettings()).isEqualTo(RelayRoomTimeLimitSettings.defaultSettings());
        assertThat(provider.currentRoomTimeLimitSettings()).isEqualTo(RelayRoomTimeLimitSettings.defaultSettings());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"default\":45}", "{\"allowed\":[30,45,60]}",
        "{\"default\":45,\"allowed\":[]}", "{\"default\":45,\"allowed\":[30,60]}",
        "{\"default\":\"45\",\"allowed\":[30,45,60]}", "{\"default\":45,\"allowed\":[30,\"45\",60]}",
        "{\"default\":4,\"allowed\":[4,45,60]}", "{\"default\":601,\"allowed\":[45,601]}", "[]"})
    void currentRoomTimeLimitSettingsFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY))
            .willReturn(Optional
                .of(systemParameter(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY, settingValue)));

        assertThat(provider.currentRoomTimeLimitSettings()).isEqualTo(RelayRoomTimeLimitSettings.defaultSettings());
    }

    @Test
    void currentReconnectGracePeriodReturnsConfiguredValue() {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY,
                "{\"value\":30,\"unit\":\"seconds\"}")));

        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void currentReconnectGracePeriodAllowsZeroSeconds() {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional.of(systemParameter(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY,
                "{\"value\":0,\"unit\":\"seconds\"}")));

        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ZERO);
    }

    @Test
    void currentReconnectGracePeriodFallsBackWhenSettingMissingOrBlank() {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional.empty()).willReturn(
                Optional.of(systemParameter(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY, " ")));

        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(10));
        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"value\":\"10\"}", "{\"value\":-1}", "{\"value\":301}", "[]"})
    void currentReconnectGracePeriodFallsBackWhenSettingInvalid(String settingValue) {
        given(systemParameterRepository.findByKey(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY))
            .willReturn(Optional
                .of(systemParameter(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY, settingValue)));

        assertThat(provider.currentReconnectGracePeriod()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void currentSettingsSnapshotLoadsRelaySettingsInSingleQuery() {
        given(systemParameterRepository.findAllByKeys(anyList())).willReturn(List.of(
            systemParameter(RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY,
                "{\"min\":3,\"max\":8,\"unit\":\"people\"}"),
            systemParameter(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                "{\"default\":60,\"allowed\":[45,60,90],\"unit\":\"seconds\"}"),
            systemParameter(RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY,
                "{\"value\":30,\"unit\":\"seconds\"}")));

        RelayRuntimeSettingsSnapshot snapshot = provider.currentSettingsSnapshot();

        assertThat(snapshot.participantLimit()).isEqualTo(new RelayRoomParticipantLimit(3, 8));
        assertThat(snapshot.roomTimeLimitSettings())
            .isEqualTo(new RelayRoomTimeLimitSettings(60, java.util.Set.of(45, 60, 90)));
        assertThat(snapshot.reconnectGracePeriod()).isEqualTo(Duration.ofSeconds(30));
        verify(systemParameterRepository)
            .findAllByKeys(List.of(RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY,
                RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                RelayRuntimeSettingsProvider.RECONNECT_GRACE_SECONDS_SETTING_KEY));
    }

    @Test
    void currentSettingsSnapshotFallsBackIndependently() {
        given(systemParameterRepository.findAllByKeys(anyList()))
            .willReturn(List.of(systemParameter(RelayRuntimeSettingsProvider.PARTICIPANT_LIMIT_SETTING_KEY, "not-json"),
                systemParameter(RelayRuntimeSettingsProvider.ROOM_TIME_LIMIT_SECONDS_SETTING_KEY,
                    "{\"default\":60,\"allowed\":[45,60,90],\"unit\":\"seconds\"}")));

        RelayRuntimeSettingsSnapshot snapshot = provider.currentSettingsSnapshot();

        assertThat(snapshot.participantLimit()).isEqualTo(RelayRoomParticipantLimit.defaultLimit());
        assertThat(snapshot.roomTimeLimitSettings())
            .isEqualTo(new RelayRoomTimeLimitSettings(60, java.util.Set.of(45, 60, 90)));
        assertThat(snapshot.reconnectGracePeriod()).isEqualTo(Duration.ofSeconds(10));
    }

    private SystemParameter systemParameter(String key, String value) {
        LocalDateTime now = LocalDateTime.now();

        return new SystemParameter(1L, key, value, 0L, null, now, now);
    }
}
