package com.nemonicworld.relay.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Relay current part submission response")
public record RelayRoomSubmissionResponse(@Schema(description = "Room code", example = "AB3K9Q") String roomCode,
    @Schema(description = "Submitted canvas index", example = "1") int canvasIndex,
    @Schema(description = "Submitted part", example = "BODY") RelayDrawingPart part,
    @Schema(description = "Assignment status after submission", example = "SUBMITTED") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "Drawing image MinIO object key", example = "relay/tmp/AB3K9Q/1/body.png") String drawingObjectKey,
    @Schema(description = "Hint image MinIO object key", nullable = true, example = "relay/tmp/AB3K9Q/1/body-hint.png") String hintObjectKey,
    @Schema(description = "Submitted at", example = "2026-05-05T14:00:31") LocalDateTime submittedAt,
    @Schema(description = "Whether this request reused an existing submission", example = "false") boolean alreadySubmitted,
    @Schema(description = "Whether the submitted part is now completed by everyone", example = "false") boolean currentPartCompleted,
    @Schema(description = "Completed submission count for the submitted part", example = "2") int submittedCount,
    @Schema(description = "Total assignment count for the submitted part", example = "3") int totalCount,
    @Schema(description = "Whether this submission advanced the room", example = "true") boolean advanced,
    @Schema(description = "Next part after advancement", nullable = true, example = "LEGS") RelayDrawingPart nextPart,
    @Schema(description = "Next part started at", nullable = true, example = "2026-05-05T14:00:31") LocalDateTime nextPartStartedAt,
    @Schema(description = "Next part deadline at", nullable = true, example = "2026-05-05T14:01:16") LocalDateTime nextPartDeadlineAt,
    @Schema(description = "Whether every part has been completed", example = "false") boolean allPartsCompleted,
    @Schema(description = "Room status after submission", example = "PLAYING") RelayRoomStatus roomStatus,
    @JsonIgnore @Schema(hidden = true) String userUuid, @JsonIgnore @Schema(hidden = true) String nickname) {

    public static RelayRoomSubmissionResponse from(String roomCode, RelayRoomAssignment assignment,
        boolean alreadySubmitted, boolean currentPartCompleted, int submittedCount, int totalCount, String userUuid,
        String nickname, boolean advanced, RelayDrawingPart nextPart, LocalDateTime nextPartStartedAt,
        LocalDateTime nextPartDeadlineAt, boolean allPartsCompleted, RelayRoomStatus roomStatus) {
        return new RelayRoomSubmissionResponse(roomCode, assignment.canvasIndex(), assignment.part(),
            assignment.status(), assignment.objectKey(), assignment.hintObjectKey(), assignment.submittedAt(),
            alreadySubmitted, currentPartCompleted, submittedCount, totalCount, advanced, nextPart, nextPartStartedAt,
            nextPartDeadlineAt, allPartsCompleted, roomStatus, userUuid, nickname);
    }
}
