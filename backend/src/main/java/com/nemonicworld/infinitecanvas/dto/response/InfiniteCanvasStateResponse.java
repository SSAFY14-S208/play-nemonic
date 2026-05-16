package com.nemonicworld.infinitecanvas.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "무한 캔버스 상태 응답")
public record InfiniteCanvasStateResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "캔버스 상태") InfiniteCanvasStatus status, @Schema(description = "소유자 UUID") String ownerUserUuid,
    @Schema(description = "내 참여자 정보", nullable = true) InfiniteCanvasParticipantResponse me,
    @Schema(description = "참여자 목록") List<InfiniteCanvasParticipantResponse> participants,
    @Schema(description = "캔버스 요소 목록") List<JsonNode> elements,
    @Schema(description = "최근 편집 연산 목록") List<InfiniteCanvasOperation> operations,
    @Schema(description = "요소 lock 상태") Map<String, InfiniteCanvasLock> locks,
    @Schema(description = "뷰포트 정보", nullable = true) JsonNode viewport,
    @Schema(description = "최대 참여자 수") int maxParticipants, @Schema(description = "현재 revision") long revision,
    @Schema(description = "생성 시각") LocalDateTime createdAt, @Schema(description = "수정 시각") LocalDateTime updatedAt) {

    public static InfiniteCanvasStateResponse from(InfiniteCanvasState state, String currentUserUuid) {
        InfiniteCanvasParticipantResponse me = state.findParticipant(currentUserUuid)
            .map(InfiniteCanvasParticipantResponse::from).orElse(null);

        return new InfiniteCanvasStateResponse(state.roomCode(), state.status(), state.ownerUserUuid(), me,
            state.participants().stream().map(InfiniteCanvasParticipantResponse::from).toList(), state.elements(),
            state.operations(), state.locks(), state.viewport(), state.maxParticipants(), state.revision(),
            state.createdAt(), state.updatedAt());
    }
}
