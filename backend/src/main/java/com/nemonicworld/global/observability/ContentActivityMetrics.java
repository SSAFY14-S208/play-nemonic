package com.nemonicworld.global.observability;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ContentActivityMetrics {

    private static final Set<RelayRoomStatus> RELAY_ACTIVE = EnumSet.of(RelayRoomStatus.WAITING,
        RelayRoomStatus.PLAYING, RelayRoomStatus.FINALIZING);
    private static final Set<FlipbookRoomStatus> FLIPBOOK_ACTIVE = EnumSet.of(FlipbookRoomStatus.WAITING,
        FlipbookRoomStatus.PLAYING, FlipbookRoomStatus.FINALIZING);

    private final MeterRegistry registry;
    private final RelayRoomRepository relayRoomRepository;
    private final FlipbookRoomRepository flipbookRoomRepository;

    public ContentActivityMetrics(MeterRegistry registry, RelayRoomRepository relayRoomRepository,
        FlipbookRoomRepository flipbookRoomRepository) {
        this.registry = registry;
        this.relayRoomRepository = relayRoomRepository;
        this.flipbookRoomRepository = flipbookRoomRepository;
    }

    @PostConstruct
    void register() {
        Gauge.builder("nemonic.content.active.rooms", this, ContentActivityMetrics::countRelayActiveRooms)
            .tag("content_type", "relay").description("Rooms currently in active status").register(registry);

        Gauge.builder("nemonic.content.active.rooms", this, ContentActivityMetrics::countFlipbookActiveRooms)
            .tag("content_type", "flipbook").description("Rooms currently in active status").register(registry);
    }

    private long countRelayActiveRooms() {
        return relayRoomRepository.findAllActiveRooms().stream()
            .filter(roomState -> RELAY_ACTIVE.contains(roomState.status())).count();
    }

    private long countFlipbookActiveRooms() {
        return flipbookRoomRepository.findAllActiveRooms().stream()
            .filter(roomState -> FLIPBOOK_ACTIVE.contains(roomState.status())).count();
    }
}
