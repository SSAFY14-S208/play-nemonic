package com.nemonicworld.relay.service.finalization;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomFinalizationAsyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomFinalizationAsyncTrigger.class);
    private static final String IMMEDIATE_TRIGGER_FAILED_EVENT = "relay_finalization_immediate_trigger_failed";

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
            logTriggerFailure(roomCode, "task_submit", e);
            log.warn("릴레이 최종 결과물 비동기 생성 작업을 시작하지 못했습니다. roomCode={}", roomCode, e);
        }
    }

    private void triggerSafely(String roomCode) {
        try {
            relayRoomFinalizationService.triggerFinalization(roomCode);
        } catch (RuntimeException e) {
            logTriggerFailure(roomCode, "async_run", e);
            log.warn("릴레이 최종 결과물 비동기 생성 중 오류가 발생했습니다. roomCode={}", roomCode, e);
        }
    }

    private void logTriggerFailure(String roomCode, String stage, RuntimeException error) {
        RelayRoomEventLogger.apiWarn(IMMEDIATE_TRIGGER_FAILED_EVENT,
            "failed to run relay finalization immediate trigger",
            metadata("room_id", roomCode, "stage", stage, "error", error.getClass().getSimpleName()), error);
    }
}
