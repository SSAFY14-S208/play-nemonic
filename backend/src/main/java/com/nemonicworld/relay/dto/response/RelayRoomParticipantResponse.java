package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayRoomParticipant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "릴레이 방 참여자 응답")
public record RelayRoomParticipantResponse(
    @Schema(description = "참여자 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "참여자 닉네임", example = "망고") String nickname,
    @Schema(description = "방장 여부", example = "true") boolean host,
    @Schema(description = "입장 순서", example = "0") int joinOrder,
    @Schema(description = "연결 여부", example = "true") boolean connected) {

    public static RelayRoomParticipantResponse from(RelayRoomParticipant participant) {
        return new RelayRoomParticipantResponse(participant.userUuid(), participant.nickname(), participant.host(),
            participant.joinOrder(), participant.connected());
    }
}
