package com.nemonicworld.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentActivityMetricsTest {

    private RelayRoomRepository relayRoomRepository;
    private FlipbookRoomRepository flipbookRoomRepository;
    private SimpleMeterRegistry registry;
    private ContentActivityMetrics metrics;

    @BeforeEach
    void setUp() {
        relayRoomRepository = mock(RelayRoomRepository.class);
        flipbookRoomRepository = mock(FlipbookRoomRepository.class);
        registry = new SimpleMeterRegistry();
        metrics = new ContentActivityMetrics(registry, relayRoomRepository, flipbookRoomRepository);
        metrics.register();
    }

    @Test
    void countsRelayRoomsBeforeFinishedAsActive() {
        given(relayRoomRepository.countActiveRoomsByStatuses(
            Set.of(RelayRoomStatus.WAITING, RelayRoomStatus.PLAYING, RelayRoomStatus.FINALIZING))).willReturn(3L);

        assertThat(activeRooms("relay")).isEqualTo(3.0);
    }

    @Test
    void countsFlipbookRoomsBeforeFinishedAsActive() {
        given(flipbookRoomRepository.countActiveRoomsByStatuses(
            Set.of(FlipbookRoomStatus.WAITING, FlipbookRoomStatus.PLAYING, FlipbookRoomStatus.FINALIZING)))
            .willReturn(3L);

        assertThat(activeRooms("flipbook")).isEqualTo(3.0);
    }

    private double activeRooms(String contentType) {
        return registry.get("nemonic.content.active.rooms").tag("content_type", contentType).gauge().value();
    }
}
