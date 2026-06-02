package com.nemonicworld.flipbook.service.submission;

import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.service.game.FlipbookRoundAdvanceResult;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FlipbookFrameSubmissionEventSupport {

    public void logFrameSubmissionRejected(String userUuidValue, String roomCodeValue, int round,
        FlipbookFrameSubmitRequest request, RuntimeException e) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_submission_rejected",
            metadata("room_id", roomCodeValue, "uuid", safeUuid(userUuidValue), "round", round, "flipbook_index",
                request == null ? null : request.flipbookIndex(), "frame_index",
                request == null ? null : request.frameIndex(), "file_id", request == null ? null : request.fileId(),
                "result", "rejected", "reason_code", e.getClass().getSimpleName()));
    }

    public void logFrameSubmitted(FlipbookRoundAdvanceResult advanceResult, FlipbookFrameAssignment assignment,
        FlipbookRoomParticipant participant, FileUpload frameFile) {
        FlipbookRoomEventLogger.apiBusiness("flipbook_frame_submitted",
            metadata("room_id", advanceResult.roomState().roomCode(), "uuid", participant.userUuid(), "round",
                assignment.round(), "flipbook_index", assignment.flipbookIndex(), "frame_index",
                assignment.frameIndex(), "file_id", frameFile.getId(), "object_key_hash",
                FlipbookRoomEventLogger.hash(frameFile.getObjectKey()), "submitted_count",
                advanceResult.progress().submittedCount(), "total_count", advanceResult.progress().totalCount(),
                "room_status", advanceResult.roomState().status()));
        if (advanceResult.allRoundsCompleted()) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_all_rounds_completed",
                metadata("room_id", advanceResult.roomState().roomCode(), "room_status",
                    advanceResult.roomState().status(), "total_rounds", advanceResult.roomState().totalRounds()));
        } else if (advanceResult.advanced()) {
            FlipbookRoomEventLogger.apiBusiness("flipbook_round_started",
                metadata("room_id", advanceResult.roomState().roomCode(), "round", advanceResult.nextRound(),
                    "round_deadline_at", advanceResult.nextRoundDeadlineAt()));
        }
    }

    private String safeUuid(String userUuidValue) {
        if (!StringUtils.hasText(userUuidValue)) {
            return null;
        }

        try {
            return UUID.fromString(userUuidValue).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
