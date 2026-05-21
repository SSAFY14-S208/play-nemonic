package com.nemonicworld.flipbook.dto.websocket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "플립북 라운드 제한 시간 종료 WebSocket 이벤트")
public record FlipbookRoundTimeUpEventResponse(@Schema(description = "방 코드", example = "FB3K9Q") String roomCode,
    @Schema(description = "제한 시간이 종료된 라운드", example = "2") int round,
    @Schema(description = "라운드 마감 시각", example = "2026-05-08T14:01:16") LocalDateTime roundDeadlineAt,
    @Schema(description = "자동 제출 전까지 제출을 허용하는 시각", example = "2026-05-08T14:01:18") LocalDateTime submitGraceDeadlineAt,
    @Schema(description = "자동 제출 유예 시간(ms)", example = "5000") long autoSubmitGraceMillis,
    @Schema(description = "미제출 배정 수", example = "2") int pendingCount,
    @Schema(description = "현재 라운드 미제출 배정 목록") List<PendingSubmission> pendingSubmissions) {

    public FlipbookRoundTimeUpEventResponse {
        pendingSubmissions = pendingSubmissions == null ? List.of() : List.copyOf(pendingSubmissions);
        pendingCount = pendingSubmissions.size();
    }

    @Schema(description = "플립북 현재 라운드 미제출 배정")
    public record PendingSubmission(@Schema(description = "플립북 번호", example = "1") int flipbookIndex,
        @Schema(description = "프레임 번호", example = "3") int frameIndex,
        @Schema(description = "미제출 사용자 UUID") String userUuid, @Schema(description = "미제출 사용자 닉네임") String nickname,
        @Schema(description = "현재 WebSocket 연결 여부", example = "true") boolean connected) {
    }
}
