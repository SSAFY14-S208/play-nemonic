package com.nemonicworld.flipbook.scheduler;

import com.nemonicworld.flipbook.service.disconnect.FlipbookRoomDisconnectGraceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게임 중 재접속 유예가 끝난 플립북 참여자를 주기적으로 이탈 확정 처리합니다.
 */
@Component
@ConditionalOnProperty(prefix = "nemonic.flipbook.disconnect", name = "enabled", havingValue = "true")
public class FlipbookRoomDisconnectGraceScheduler {

    private final FlipbookRoomDisconnectGraceService flipbookRoomDisconnectGraceService;

    public FlipbookRoomDisconnectGraceScheduler(FlipbookRoomDisconnectGraceService flipbookRoomDisconnectGraceService) {
        this.flipbookRoomDisconnectGraceService = flipbookRoomDisconnectGraceService;
    }

    /**
     * 설정된 주기마다 게임 중 이탈 확정 후보 플립북 방을 스캔합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.flipbook.disconnect.scan-delay-ms:1000}")
    public void processDroppedParticipants() {
        flipbookRoomDisconnectGraceService.processDroppedParticipants();
    }
}
