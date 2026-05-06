package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.close.RelayRoomCloseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FINISHED 상태로 결과 확인 시간이 지난 릴레이 방을 주기적으로 CLOSED 처리합니다.
 */
@Component
public class RelayRoomCloseScheduler {

    private final RelayRoomCloseService relayRoomCloseService;
    private final boolean enabled;

    public RelayRoomCloseScheduler(RelayRoomCloseService relayRoomCloseService,
        @Value("${nemonic.relay.close.enabled:true}") boolean enabled) {
        this.relayRoomCloseService = relayRoomCloseService;
        this.enabled = enabled;
    }

    /**
     * 설정된 주기마다 close 가능한 FINISHED 방을 처리합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.close.scan-delay-ms:30000}")
    public void closeFinishedRooms() {
        if (!enabled) {
            return;
        }
        relayRoomCloseService.closeFinishedRooms();
    }
}
