package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "무한 캔버스 참여자 응답")
public record InfiniteCanvasParticipantResponse(@Schema(description = "참여자 UUID") String userUuid,
    @Schema(description = "닉네임") String nickname, @Schema(description = "색상") String color,
    @Schema(description = "아바타 URL", nullable = true) String avatarUrl,
    @Schema(description = "WebSocket 연결 여부") boolean connected, @Schema(description = "입장 시각") LocalDateTime joinedAt,
    @Schema(description = "마지막 연결 시각", nullable = true) LocalDateTime lastConnectedAt) {

    public static InfiniteCanvasParticipantResponse from(InfiniteCanvasParticipant participant) {
        return new InfiniteCanvasParticipantResponse(participant.userUuid(), participant.nickname(),
            participant.color(), participant.avatarUrl(), participant.connected(), participant.joinedAt(),
            participant.lastConnectedAt());
    }
}
