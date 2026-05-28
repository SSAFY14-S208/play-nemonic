package com.nemonicworld.relay.service.submission;

import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayPartProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RelaySubmissionEventSupport {

    private static final Logger log = LoggerFactory.getLogger(RelaySubmissionEventSupport.class);

    public void logSubmitted(RelayRoomState roomState, RelayRoomAssignment assignment, String userUuid,
        RelayPartAdvanceResult advanceResult) {
        RelayPartProgress progress = advanceResult.progress();
        RelayRoomEventLogger.apiBusiness("relay_drawing_submitted",
            metadata("room_id", roomState.roomCode(), "uuid", userUuid, "canvas_index", assignment.canvasIndex(),
                "part", assignment.part(), "submitted_at", assignment.submittedAt(), "current_part_completed",
                progress.currentPartCompleted()));
    }

    public void logAdvanceEvents(RelayPartAdvanceResult advanceResult, RelayDrawingPart previousPart) {
        if (!advanceResult.advanced()) {
            return;
        }

        RelayRoomState roomState = advanceResult.roomState();
        if (advanceResult.allPartsCompleted()) {
            RelayRoomEventLogger.apiBusiness("relay_all_parts_completed",
                metadata("room_id", roomState.roomCode(), "participant_count", roomState.participantCount(),
                    "assignment_count", roomState.assignments().size(), "completed_at", roomState.updatedAt()));
            return;
        }

        RelayRoomEventLogger.apiBusiness("relay_part_started",
            metadata("room_id", roomState.roomCode(), "part", advanceResult.nextPart(), "previous_part", previousPart,
                "participant_count", roomState.participantCount(), "part_deadline_at",
                advanceResult.nextPartDeadlineAt()));
    }

    public void logRedisSaveConflict(String cleanupMessage, RelayRoomState roomState,
        RelayRoomAssignment currentAssignment, String viewerUserUuid, String drawingObjectKey) {
        RelayRoomEventLogger.apiWarn("relay_minio_upload_redis_save_failed", cleanupMessage,
            metadata("room_id", roomState.roomCode(), "uuid", viewerUserUuid, "canvas_index",
                currentAssignment.canvasIndex(), "part", currentAssignment.part(), "object_key_hash",
                RelayRoomEventLogger.hash(drawingObjectKey)),
            null);
        log.warn("{} roomCode={}, canvasIndex={}, part={}", cleanupMessage, roomState.roomCode(),
            currentAssignment.canvasIndex(), currentAssignment.part());
    }

    public void logCasRetryExceeded(String roomCode, int attemptCount) {
        RelayRoomEventLogger.apiWarn("relay_redis_cas_retry_exceeded",
            "relay submission exceeded Redis CAS retry count",
            metadata("room_id", roomCode, "operation", "submission", "attempt_count", attemptCount), null);
    }
}
