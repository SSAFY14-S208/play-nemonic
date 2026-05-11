package com.nemonicworld.flipbook.scheduler;

import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FINALIZING 상태의 플립북 방을 주기적으로 찾아 GIF 결과물을 생성합니다.
 */
@Component
public class FlipbookRoomFinalizationScheduler {

    private final FlipbookRoomFinalizationService flipbookRoomFinalizationService;
    private final boolean enabled;

    public FlipbookRoomFinalizationScheduler(FlipbookRoomFinalizationService flipbookRoomFinalizationService,
        @Value("${nemonic.flipbook.finalization.enabled:true}") boolean enabled) {
        this.flipbookRoomFinalizationService = flipbookRoomFinalizationService;
        this.enabled = enabled;
    }

    /**
     * 설정된 주기마다 최종화 대기 방을 처리합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.flipbook.finalization.scan-delay-ms:2000}")
    public void processFinalizingRooms() {
        if (!enabled) {
            return;
        }
        flipbookRoomFinalizationService.processFinalizingRooms();
    }
}
