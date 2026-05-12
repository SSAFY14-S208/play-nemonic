package com.nemonicworld.relay.service.support;

import java.time.Duration;

public record RelayRuntimeSettingsSnapshot(RelayRoomParticipantLimit participantLimit,
    RelayRoomTimeLimitSettings roomTimeLimitSettings, Duration reconnectGracePeriod) {
}
