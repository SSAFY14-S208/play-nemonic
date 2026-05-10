package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "릴레이 파트 제한 시간 종료 WebSocket 이벤트")
public record RelayRoomPartTimeUpEventResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "제한 시간이 종료된 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "파트 마감 시각", example = "2026-05-05T14:01:16") LocalDateTime partDeadlineAt,
    @Schema(description = "자동 제출 전까지 제출이 허용되는 시각", example = "2026-05-05T14:01:18") LocalDateTime submitGraceDeadlineAt,
    @Schema(description = "자동 제출 유예 시간(ms)", example = "2000") long autoSubmitGraceMillis,
    @Schema(description = "미제출 배정 수", example = "2") int pendingCount,
    @Schema(description = "현재 파트 미제출 배정 목록") List<PendingSubmission> pendingSubmissions) {

    public RelayRoomPartTimeUpEventResponse {
        pendingSubmissions = pendingSubmissions == null ? List.of() : List.copyOf(pendingSubmissions);
        pendingCount = pendingSubmissions.size();
    }

    @Schema(description = "릴레이 현재 파트 미제출 배정")
    public record PendingSubmission(@Schema(description = "캔버스 번호", example = "1") int canvasIndex,
        @Schema(description = "미제출 사용자 UUID") String userUuid, @Schema(description = "미제출 사용자 닉네임") String nickname,
        @Schema(description = "현재 WebSocket 연결 여부", example = "true") boolean connected) {
    }
}
