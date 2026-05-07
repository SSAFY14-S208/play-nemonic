package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.timeout.RelayRoomTimeoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 릴레이 현재 파트 마감 시간이 지난 방을 주기적으로 자동 제출 처리합니다.
 */
@Component
@ConditionalOnProperty(prefix = "nemonic.relay.timeout", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RelayRoomTimeoutScheduler {

    private final RelayRoomTimeoutService relayRoomTimeoutService;

    public RelayRoomTimeoutScheduler(RelayRoomTimeoutService relayRoomTimeoutService) {
        this.relayRoomTimeoutService = relayRoomTimeoutService;
    }

    /**
     * 설정된 주기마다 만료된 릴레이 방을 스캔합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.timeout.scan-delay-ms:1000}")
    public void processExpiredRooms() {
        relayRoomTimeoutService.processExpiredRooms();
    }
}
