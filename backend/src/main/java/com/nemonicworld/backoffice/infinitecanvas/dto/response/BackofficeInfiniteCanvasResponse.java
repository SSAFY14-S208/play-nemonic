package com.nemonicworld.backoffice.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "백오피스 활성 무한 캔버스 응답")
public record BackofficeInfiniteCanvasResponse(@Schema(description = "공유 방코드", example = "AC3K9Q") String roomCode,

    @Schema(description = "캔버스 상태", example = "ACTIVE") InfiniteCanvasStatus status,

    @Schema(description = "방장 사용자 UUID") String hostUserUuid,

    @Schema(description = "전체 참여자 수", example = "4") int participantCount,

    @Schema(description = "최대 참여자 수", example = "8") int maxParticipants,

    @Schema(description = "현재 WebSocket 연결 참여자 수", example = "3") int connectedParticipantCount,

    @Schema(description = "캔버스 요소 수", example = "42") int elementCount,

    @Schema(description = "현재 revision", example = "17") long revision,

    @Schema(description = "생성 시각") LocalDateTime createdAt,

    @Schema(description = "마지막 갱신 시각") LocalDateTime updatedAt) {

    public static BackofficeInfiniteCanvasResponse from(InfiniteCanvasState state) {
        return new BackofficeInfiniteCanvasResponse(state.roomCode(), state.status(), state.hostUserUuid(),
            state.participantCount(), state.maxParticipants(), state.connectedParticipantCount(),
            state.elements().size(), state.revision(), state.createdAt(), state.updatedAt());
    }
}
