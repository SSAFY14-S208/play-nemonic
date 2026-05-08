package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플립북 방 참여자 응답")
public record FlipbookRoomParticipantResponse(@Schema(description = "참여자 UUID") String userUuid,
    @Schema(description = "참여자 닉네임") String nickname, @Schema(description = "방장 여부") boolean host,
    @Schema(description = "입장 순서") int joinOrder, @Schema(description = "연결 여부") boolean connected) {

    /**
     * Redis 참여자 모델에서 클라이언트 공개 응답 필드만 변환합니다.
     */
    public static FlipbookRoomParticipantResponse from(FlipbookRoomParticipant participant) {
        return new FlipbookRoomParticipantResponse(participant.userUuid(), participant.nickname(), participant.host(),
            participant.joinOrder(), participant.connected());
    }
}
