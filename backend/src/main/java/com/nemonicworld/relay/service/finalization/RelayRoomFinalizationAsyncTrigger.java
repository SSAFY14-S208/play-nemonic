package com.nemonicworld.relay.service.finalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 마지막 파트 완료 후 최종화를 요청 처리 흐름과 분리해서 실행합니다.
 */
@Component
public class RelayRoomFinalizationAsyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomFinalizationAsyncTrigger.class);

    private final RelayRoomFinalizationService relayRoomFinalizationService;
    private final TaskExecutor taskExecutor;

    public RelayRoomFinalizationAsyncTrigger(RelayRoomFinalizationService relayRoomFinalizationService,
        @Qualifier(RelayRoomFinalizationAsyncConfig.RELAY_FINALIZATION_TASK_EXECUTOR) TaskExecutor taskExecutor) {
        this.relayRoomFinalizationService = relayRoomFinalizationService;
        this.taskExecutor = taskExecutor;
    }

    public void trigger(String roomCode) {
        try {
            taskExecutor.execute(() -> triggerSafely(roomCode));
        } catch (RuntimeException e) {
            log.warn("릴레이 최종 결과물 비동기 생성 작업을 시작하지 못했습니다. roomCode={}", roomCode, e);
        }
    }

    private void triggerSafely(String roomCode) {
        try {
            relayRoomFinalizationService.triggerFinalization(roomCode);
        } catch (RuntimeException e) {
            log.warn("릴레이 최종 결과물 비동기 생성 중 오류가 발생했습니다. roomCode={}", roomCode, e);
        }
    }
}
