package com.nemonicworld.flipbook.service.game;

import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationTriggerService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import org.springframework.stereotype.Component;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

@Component
public class FlipbookRoundTransitionUseCase {

    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService;

    public FlipbookRoundTransitionUseCase(FlipbookRoomEventPublisher flipbookRoomEventPublisher,
        FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService) {
        this.flipbookRoomEventPublisher = flipbookRoomEventPublisher;
        this.flipbookRoomFinalizationTriggerService = flipbookRoomFinalizationTriggerService;
    }

    public void publishTransitionEvents(String roomCode, Integer previousRound,
        FlipbookRoundAdvanceResult advanceResult) {
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allRoundsCompleted()) {
            flipbookRoomEventPublisher.publishAllRoundsCompleted(roomCode, advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_all_rounds_completed",
                metadata("room_id", roomCode, "room_status", advanceResult.roomState().status(), "total_rounds",
                    advanceResult.roomState().totalRounds()));
            flipbookRoomFinalizationTriggerService.triggerFinalizationAsync(roomCode);
        } else {
            if (previousRound == null) {
                return;
            }
            flipbookRoomEventPublisher.publishRoundStarted(roomCode, previousRound, advanceResult.nextRound(),
                advanceResult.nextRoundStartedAt(), advanceResult.nextRoundDeadlineAt());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_round_started",
                metadata("room_id", roomCode, "previous_round", previousRound, "round", advanceResult.nextRound(),
                    "round_deadline_at", advanceResult.nextRoundDeadlineAt()));
        }
    }
}
