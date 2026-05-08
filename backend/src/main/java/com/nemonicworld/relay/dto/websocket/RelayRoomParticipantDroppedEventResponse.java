package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.service.disconnect.RelayDroppedParticipantResult;
import java.time.LocalDateTime;

/**
 * 게임 중 재접속 유예가 만료되어 참여자가 이탈 확정되었음을 알리는 이벤트 payload입니다.
 */
public record RelayRoomParticipantDroppedEventResponse(String roomCode, String userUuid, String nickname,
    LocalDateTime disconnectedAt, LocalDateTime droppedAt) {

    public static RelayRoomParticipantDroppedEventResponse from(RelayDroppedParticipantResult result) {
        return new RelayRoomParticipantDroppedEventResponse(result.roomCode(), result.userUuid(), result.nickname(),
            result.disconnectedAt(), result.droppedAt());
    }
}
