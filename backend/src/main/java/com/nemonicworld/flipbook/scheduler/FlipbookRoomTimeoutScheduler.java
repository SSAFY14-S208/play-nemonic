package com.nemonicworld.flipbook.scheduler;

import com.nemonicworld.flipbook.service.timeout.FlipbookRoomTimeoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 플립북 현재 라운드 마감 시간이 지난 방을 주기적으로 자동 제출 처리합니다.
 */
@Component
@ConditionalOnProperty(value = "nemonic.flipbook.timeout.enabled", havingValue = "true", matchIfMissing = true)
public class FlipbookRoomTimeoutScheduler {

    private final FlipbookRoomTimeoutService flipbookRoomTimeoutService;

    public FlipbookRoomTimeoutScheduler(FlipbookRoomTimeoutService flipbookRoomTimeoutService) {
        this.flipbookRoomTimeoutService = flipbookRoomTimeoutService;
    }

    /**
     * 설정된 주기마다 만료된 플립북 방을 스캔합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.flipbook.timeout.scan-delay-ms:1000}")
    public void processExpiredRooms() {
        flipbookRoomTimeoutService.processExpiredRooms();
    }
}
