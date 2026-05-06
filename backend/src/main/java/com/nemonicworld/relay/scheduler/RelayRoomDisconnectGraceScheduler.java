package com.nemonicworld.relay.scheduler;

import com.nemonicworld.relay.service.disconnect.RelayRoomDisconnectGraceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게임 중 재접속 유예가 끝난 릴레이 참여자의 배정을 주기적으로 자동 제출 처리합니다.
 */
@Component
@ConditionalOnProperty(prefix = "nemonic.relay.disconnect", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RelayRoomDisconnectGraceScheduler {

    private final RelayRoomDisconnectGraceService relayRoomDisconnectGraceService;

    public RelayRoomDisconnectGraceScheduler(RelayRoomDisconnectGraceService relayRoomDisconnectGraceService) {
        this.relayRoomDisconnectGraceService = relayRoomDisconnectGraceService;
    }

    /**
     * 설정된 주기마다 게임 중 이탈 확정 후보 릴레이 방을 스캔합니다.
     */
    @Scheduled(fixedDelayString = "${nemonic.relay.disconnect.scan-delay-ms:1000}")
    public void processDroppedParticipants() {
        relayRoomDisconnectGraceService.processDroppedParticipants();
    }
}
