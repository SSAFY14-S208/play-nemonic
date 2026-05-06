package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.redis.RelayRoomParticipant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "릴레이 방 참여자 응답")
/**
 * 대기 화면 참여자 목록에 노출할 참여자 정보입니다.
 */
public record RelayRoomParticipantResponse(
    @Schema(description = "참여자 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "참여자 닉네임", example = "망고") String nickname,
    @Schema(description = "방장 여부", example = "true") boolean host,
    @Schema(description = "입장 순서", example = "0") int joinOrder,
    @Schema(description = "연결 여부", example = "true") boolean connected) {

    /**
     * Redis 저장 모델에서 API 응답에 노출하지 않는 joinedAt을 제외하고 변환합니다.
     */
    public static RelayRoomParticipantResponse from(RelayRoomParticipant participant) {
        // joinedAt/disconnectedAt은 재접속 계산용 내부 상태이므로 목록 표시 응답에서는 제외합니다.
        return new RelayRoomParticipantResponse(participant.userUuid(), participant.nickname(), participant.host(),
            participant.joinOrder(), participant.connected());
    }
}
