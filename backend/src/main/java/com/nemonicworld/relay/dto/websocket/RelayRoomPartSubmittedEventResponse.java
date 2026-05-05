package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 파트 제출 WebSocket 이벤트 응답")
public record RelayRoomPartSubmittedEventResponse(@Schema(description = "제출 사용자 UUID") String userUuid,
    @Schema(description = "제출 사용자 닉네임") String nickname, @Schema(description = "캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "제출 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "배정 상태", example = "SUBMITTED") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "제출 시각", example = "2026-05-05T14:00:31") LocalDateTime submittedAt,
    @Schema(description = "현재 파트 제출 완료 수", example = "2") int submittedCount,
    @Schema(description = "현재 파트 전체 배정 수", example = "3") int totalCount,
    @Schema(description = "현재 파트 전체 제출 완료 여부", example = "false") boolean currentPartCompleted) {

    public static RelayRoomPartSubmittedEventResponse from(RelayRoomSubmissionResponse response) {
        return new RelayRoomPartSubmittedEventResponse(response.userUuid(), response.nickname(), response.canvasIndex(),
            response.part(), response.assignmentStatus(), response.submittedAt(), response.submittedCount(),
            response.totalCount(), response.currentPartCompleted());
    }
}
