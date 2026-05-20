package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "무한 캔버스 생성 응답")
public record InfiniteCanvasCreateResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "캔버스 상태") InfiniteCanvasStatus status, @Schema(description = "방장 UUID") String hostUserUuid,
    @Schema(description = "최대 참여자 수") int maxParticipants, @Schema(description = "현재 참여자 수") int participantCount,
    @Schema(description = "현재 참여자 목록") List<InfiniteCanvasParticipantResponse> participants,
    @Schema(description = "생성 시각") LocalDateTime createdAt) {

    public static InfiniteCanvasCreateResponse from(InfiniteCanvasState state) {
        return new InfiniteCanvasCreateResponse(state.roomCode(), state.status(), state.hostUserUuid(),
            state.maxParticipants(), state.participantCount(),
            state.participants().stream().map(InfiniteCanvasParticipantResponse::from).toList(), state.createdAt());
    }
}
