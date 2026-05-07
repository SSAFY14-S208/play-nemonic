package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FINALIZING 상태의 릴레이 방을 주기적으로 찾아 최종 결과물을 생성합니다.
 */
@Component
public class RelayRoomFinalizationScheduler {

    private final RelayRoomFinalizationService relayRoomFinalizationService;
    private final boolean enabled;

    public RelayRoomFinalizationScheduler(RelayRoomFinalizationService relayRoomFinalizationService,
        @Value("${nemonic.relay.finalization.enabled:true}") boolean enabled) {
        this.relayRoomFinalizationService = relayRoomFinalizationService;
        this.enabled = enabled;
    }

    /**
     * 설정된 주기마다 최종화 대기 방을 처리합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.finalization.scan-delay-ms:2000}")
    public void processFinalizingRooms() {
        if (!enabled) {
            return;
        }
        relayRoomFinalizationService.processFinalizingRooms();
    }
}
