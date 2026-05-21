package com.nemonicworld.flipbook.scheduler;

import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FINISHED 상태로 결과 확인 시간이 지난 플립북 방을 주기적으로 CLOSED 처리합니다.
 */
@Component
public class FlipbookRoomCloseScheduler {

    private final FlipbookRoomCloseService flipbookRoomCloseService;
    private final boolean enabled;

    public FlipbookRoomCloseScheduler(FlipbookRoomCloseService flipbookRoomCloseService,
        @Value("${nemonic.flipbook.close.enabled:true}") boolean enabled) {
        this.flipbookRoomCloseService = flipbookRoomCloseService;
        this.enabled = enabled;
    }

    /**
     * 설정된 주기마다 close 가능한 FINISHED 방을 처리합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.flipbook.close.scan-delay-ms:30000}")
    public void closeFinishedRooms() {
        if (!enabled) {
            return;
        }
        flipbookRoomCloseService.closeFinishedRooms();
    }
}
