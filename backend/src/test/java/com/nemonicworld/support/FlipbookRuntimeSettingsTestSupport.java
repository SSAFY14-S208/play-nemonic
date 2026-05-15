package com.nemonicworld.support;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.flipbook.service.support.FlipbookMinFramesPerFlipbookSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRoomParticipantLimit;
import com.nemonicworld.flipbook.service.support.FlipbookRoomTimeLimitSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsSnapshot;
import com.nemonicworld.flipbook.service.support.FlipbookRuntimeSettingsProvider;
import java.time.Duration;

public final class FlipbookRuntimeSettingsTestSupport {

    private FlipbookRuntimeSettingsTestSupport() {
    }

    public static FlipbookRuntimeSettingsProvider defaultFlipbookRuntimeSettingsProvider() {
        FlipbookRuntimeSettingsProvider provider = mock(FlipbookRuntimeSettingsProvider.class);
        lenient().when(provider.currentParticipantLimit()).thenReturn(FlipbookRoomParticipantLimit.defaultLimit());
        lenient().when(provider.currentRoomTimeLimitSettings())
            .thenReturn(FlipbookRoomTimeLimitSettings.defaultSettings());
        lenient().when(provider.currentMinFramesPerFlipbook())
            .thenReturn(FlipbookMinFramesPerFlipbookSettings.DEFAULT_MIN_FRAMES_PER_FLIPBOOK);
        lenient().when(provider.currentReconnectGracePeriod())
            .thenReturn(Duration.ofSeconds(FlipbookRoomPolicy.DEFAULT_RECONNECT_GRACE_SECONDS));
        lenient().when(provider.currentSettingsSnapshot()).thenReturn(defaultFlipbookRuntimeSettingsSnapshot());

        return provider;
    }

    public static FlipbookRuntimeSettingsSnapshot defaultFlipbookRuntimeSettingsSnapshot() {
        return new FlipbookRuntimeSettingsSnapshot(FlipbookRoomParticipantLimit.defaultLimit(),
            FlipbookRoomTimeLimitSettings.defaultSettings(),
            FlipbookMinFramesPerFlipbookSettings.DEFAULT_MIN_FRAMES_PER_FLIPBOOK,
            Duration.ofSeconds(FlipbookRoomPolicy.DEFAULT_RECONNECT_GRACE_SECONDS));
    }

    public static FlipbookRoomPolicy defaultFlipbookRoomPolicy(RoomCodeGenerator roomCodeGenerator,
        FlipbookRoomRepository flipbookRoomRepository) {
        return new FlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository,
            defaultFlipbookRuntimeSettingsProvider());
    }
}
