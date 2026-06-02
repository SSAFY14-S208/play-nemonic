package com.nemonicworld.relay.service.game;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationAsyncTrigger;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import org.springframework.stereotype.Component;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Component
public class RelayPartTransitionUseCase {

    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final RelayRoomFinalizationAsyncTrigger relayRoomFinalizationAsyncTrigger;

    public RelayPartTransitionUseCase(RelayRoomEventPublisher relayRoomEventPublisher,
        RelayRoomFinalizationAsyncTrigger relayRoomFinalizationAsyncTrigger) {
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.relayRoomFinalizationAsyncTrigger = relayRoomFinalizationAsyncTrigger;
    }

    public void publishTransitionEvents(String roomCode, RelayDrawingPart previousPart,
        RelayPartAdvanceResult advanceResult) {
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allPartsCompleted()) {
            relayRoomEventPublisher.publishAllPartsCompleted(roomCode, advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
            RelayRoomEventLogger.websocketBusiness("relay_all_parts_completed",
                metadata("room_id", roomCode, "participant_count", advanceResult.roomState().participantCount(),
                    "assignment_count", advanceResult.roomState().assignments().size(), "completed_at",
                    advanceResult.roomState().updatedAt()));
            relayRoomFinalizationAsyncTrigger.trigger(roomCode);
        } else {
            if (previousPart == null) {
                return;
            }
            relayRoomEventPublisher.publishPartStarted(roomCode, previousPart, advanceResult.nextPart(),
                advanceResult.nextPartStartedAt(), advanceResult.nextPartDeadlineAt());
            RelayRoomEventLogger.websocketBusiness("relay_part_started",
                metadata("room_id", roomCode, "part", advanceResult.nextPart(), "previous_part", previousPart,
                    "participant_count", advanceResult.roomState().participantCount(), "part_deadline_at",
                    advanceResult.nextPartDeadlineAt()));
        }
    }
}
