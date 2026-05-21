package com.nemonicworld.infinitecanvas.scheduler;

import com.nemonicworld.infinitecanvas.service.close.InfiniteCanvasAbandonedCloseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 빈 무한 캔버스를 주기적으로 정리하는 스케줄러. 릴레이/플립북의 {@code RoomAbandonedCloseScheduler}와 동일한
 * fixedDelay 패턴.
 */
@Component
public class InfiniteCanvasAbandonedCloseScheduler {

    private final InfiniteCanvasAbandonedCloseService infiniteCanvasAbandonedCloseService;
    private final boolean enabled;

    public InfiniteCanvasAbandonedCloseScheduler(
        InfiniteCanvasAbandonedCloseService infiniteCanvasAbandonedCloseService,
        @Value("${nemonic.infinite-canvas.abandoned-close.enabled:true}") boolean enabled) {
        this.infiniteCanvasAbandonedCloseService = infiniteCanvasAbandonedCloseService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${nemonic.infinite-canvas.abandoned-close.scan-delay-ms:30000}")
    public void closeAbandonedCanvases() {
        if (!enabled) {
            return;
        }
        infiniteCanvasAbandonedCloseService.closeAbandonedCanvases();
    }
}
