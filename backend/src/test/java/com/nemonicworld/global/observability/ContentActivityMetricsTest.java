package com.nemonicworld.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
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
        given(relayRoomRepository.findAllActiveRooms()).willReturn(
            List.of(relayRoom("RWAIT", RelayRoomStatus.WAITING), relayRoom("RPLAY", RelayRoomStatus.PLAYING),
                relayRoom("RFINZ", RelayRoomStatus.FINALIZING), relayRoom("RFINI", RelayRoomStatus.FINISHED)));

        assertThat(activeRooms("relay")).isEqualTo(3.0);
    }

    @Test
    void countsFlipbookRoomsBeforeFinishedAsActive() {
        given(flipbookRoomRepository.findAllActiveRooms()).willReturn(List.of(
            flipbookRoom("FWAIT", FlipbookRoomStatus.WAITING), flipbookRoom("FPLAY", FlipbookRoomStatus.PLAYING),
            flipbookRoom("FFINZ", FlipbookRoomStatus.FINALIZING), flipbookRoom("FFINI", FlipbookRoomStatus.FINISHED)));

        assertThat(activeRooms("flipbook")).isEqualTo(3.0);
    }

    private double activeRooms(String contentType) {
        return registry.get("nemonic.content.active.rooms").tag("content_type", contentType).gauge().value();
    }

    private RelayRoomState relayRoom(String roomCode, RelayRoomStatus status) {
        LocalDateTime now = LocalDateTime.now();

        return new RelayRoomState(roomCode, status, "host-user-uuid", 45, 2, 6, null, List.of(), now, now);
    }

    private FlipbookRoomState flipbookRoom(String roomCode, FlipbookRoomStatus status) {
        LocalDateTime now = LocalDateTime.now();

        return new FlipbookRoomState(roomCode, status, "host-user-uuid", 45, 2, 6, List.of(), now, now);
    }
}
