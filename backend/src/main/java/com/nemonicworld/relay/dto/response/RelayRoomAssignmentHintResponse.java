package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "릴레이 현재 배정 힌트 응답")
public record RelayRoomAssignmentHintResponse(
    @Schema(description = "힌트로 제공되는 이전 파트", example = "FACE") RelayDrawingPart previousPart,
    @Schema(description = "캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "힌트 이미지 객체 키", example = "relay/tmp/AB3K9Q/1/face-hint.png") String objectKey,
    @Schema(description = "힌트 이미지 URL", nullable = true) String url,
    @Schema(description = "빈 제출 여부", example = "false") boolean empty) {

    public static RelayRoomAssignmentHintResponse from(RelayRoomAssignment assignment) {
        return new RelayRoomAssignmentHintResponse(assignment.part(), assignment.canvasIndex(),
            assignment.hintObjectKey(), null, assignment.empty());
    }
}
