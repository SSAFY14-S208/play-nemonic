package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import java.time.LocalDateTime;

/**
 * 대기실에서 참여자가 강퇴되었음을 방 전체 topic에 알리는 이벤트 payload입니다.
 */
public record RelayRoomParticipantKickedEventResponse(String roomCode, String kickedUserUuid, String kickedNickname,
    int participantCount, LocalDateTime kickedAt) {

    public static RelayRoomParticipantKickedEventResponse from(RelayRoomKickResponse response) {
        return new RelayRoomParticipantKickedEventResponse(response.roomCode(), response.kickedUserUuid(),
            response.kickedNickname(), response.participantCount(), response.kickedAt());
    }
}
