package com.nemonicworld.flipbook.service.finalization;

import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FlipbookRoomFinalizationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(FlipbookRoomFinalizationTriggerService.class);

    private final FlipbookRoomFinalizationService flipbookRoomFinalizationService;
    private final Executor flipbookFinalizationExecutor;

    public FlipbookRoomFinalizationTriggerService(FlipbookRoomFinalizationService flipbookRoomFinalizationService,
        @Qualifier("flipbookFinalizationExecutor") Executor flipbookFinalizationExecutor) {
        this.flipbookRoomFinalizationService = flipbookRoomFinalizationService;
        this.flipbookFinalizationExecutor = flipbookFinalizationExecutor;
    }

    public void triggerFinalizationAsync(String roomCode) {
        try {
            flipbookFinalizationExecutor.execute(() -> triggerFinalization(roomCode));
            FlipbookRoomEventLogger.apiBusiness("flipbook_finalization_async_trigger_queued",
                metadata("room_id", roomCode));
        } catch (RuntimeException e) {
            FlipbookRoomEventLogger.apiWarn("flipbook_finalization_async_trigger_rejected",
                "플립북 최종 결과물 비동기 생성 요청을 등록할 수 없습니다.",
                metadata("room_id", roomCode, "error", e.getClass().getSimpleName()), e);
            log.warn("플립북 최종 결과물 비동기 생성 요청을 등록할 수 없습니다. roomCode={}", roomCode, e);
        }
    }

    private void triggerFinalization(String roomCode) {
        try {
            flipbookRoomFinalizationService.triggerFinalization(roomCode);
        } catch (RuntimeException e) {
            FlipbookRoomEventLogger.apiWarn("flipbook_finalization_async_trigger_failed",
                "플립북 최종 결과물 비동기 생성 중 오류가 발생했습니다.", metadata("room_id", roomCode, "error", e.getClass().getSimpleName()),
                e);
            log.warn("플립북 최종 결과물 비동기 생성 중 오류가 발생했습니다. roomCode={}", roomCode, e);
        }
    }
}
