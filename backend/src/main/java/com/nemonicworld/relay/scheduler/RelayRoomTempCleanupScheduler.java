package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.cleanup.RelayRoomTempCleanupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CLOSED 방의 릴레이 임시 이미지를 주기적으로 정리합니다.
 */
@Component
public class RelayRoomTempCleanupScheduler {

    private final RelayRoomTempCleanupService relayRoomTempCleanupService;
    private final boolean enabled;
    private final boolean oldTempEnabled;

    public RelayRoomTempCleanupScheduler(RelayRoomTempCleanupService relayRoomTempCleanupService,
        @Value("${nemonic.relay.cleanup.enabled:true}") boolean enabled,
        @Value("${nemonic.relay.cleanup.old-temp-enabled:true}") boolean oldTempEnabled) {
        this.relayRoomTempCleanupService = relayRoomTempCleanupService;
        this.enabled = enabled;
        this.oldTempEnabled = oldTempEnabled;
    }

    /**
     * cleanup marker가 없는 CLOSED 방만 대상으로 MinIO relay/tmp 파일을 삭제합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.cleanup.scan-delay-ms:30000}")
    public void cleanupClosedRooms() {
        if (!enabled) {
            return;
        }
        relayRoomTempCleanupService.cleanupClosedRooms();
    }

    /**
     * Redis room state가 먼저 사라진 경우를 대비해 오래된 relay/tmp 파일을 fallback으로 삭제합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.cleanup.old-temp-scan-delay-ms:86400000}", initialDelayString = "${nemonic.relay.cleanup.old-temp-initial-delay-ms:86400000}")
    public void cleanupOldTempObjects() {
        if (!enabled || !oldTempEnabled) {
            return;
        }
        relayRoomTempCleanupService.cleanupOldTempObjects();
    }
}
