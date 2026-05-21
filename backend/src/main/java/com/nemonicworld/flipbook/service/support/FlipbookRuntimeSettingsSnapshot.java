package com.nemonicworld.flipbook.service.support;

import java.time.Duration;

public record FlipbookRuntimeSettingsSnapshot(FlipbookRoomParticipantLimit participantLimit,
    FlipbookRoomTimeLimitSettings roomTimeLimitSettings, int minFramesPerFlipbook, Duration reconnectGracePeriod) {
}
