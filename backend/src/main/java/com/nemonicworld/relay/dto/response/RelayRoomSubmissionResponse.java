package com.nemonicworld.relay.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 현재 파트 제출 응답")
public record RelayRoomSubmissionResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "제출한 캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "제출한 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "제출 후 배정 상태", example = "SUBMITTED") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "원본 이미지 MinIO object key", example = "relay/tmp/AB3K9Q/1/body.png") String drawingObjectKey,
    @Schema(description = "힌트 key", nullable = true, example = "relay/tmp/.../body-hint.png") String hintObjectKey,
    @Schema(description = "제출 시각", example = "2026-05-05T14:00:31") LocalDateTime submittedAt,
    @Schema(description = "이미 제출된 배정의 재요청 여부", example = "false") boolean alreadySubmitted,
    @Schema(description = "현재 파트 전체 제출 완료 여부", example = "false") boolean currentPartCompleted,
    @Schema(description = "현재 파트 제출 완료 수", example = "2") int submittedCount,
    @Schema(description = "현재 파트 전체 배정 수", example = "3") int totalCount,
    @JsonIgnore @Schema(hidden = true) String userUuid, @JsonIgnore @Schema(hidden = true) String nickname) {

    public static RelayRoomSubmissionResponse from(String roomCode, RelayRoomAssignment assignment,
        boolean alreadySubmitted, boolean currentPartCompleted, int submittedCount, int totalCount, String userUuid,
        String nickname) {
        return new RelayRoomSubmissionResponse(roomCode, assignment.canvasIndex(), assignment.part(),
            assignment.status(), assignment.objectKey(), assignment.hintObjectKey(), assignment.submittedAt(),
            alreadySubmitted, currentPartCompleted, submittedCount, totalCount, userUuid, nickname);
    }
}
