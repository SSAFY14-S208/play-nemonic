package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import java.time.LocalDateTime;

/**
 * 대기실에서 참여자가 강퇴되었음을 방 전체 topic에 알리는 이벤트 payload입니다.
 */
public record FlipbookRoomParticipantKickedEventResponse(String roomCode, String kickedUserUuid, String kickedNickname,
    int participantCount, LocalDateTime kickedAt) {

    public static FlipbookRoomParticipantKickedEventResponse from(FlipbookRoomKickResponse response) {
        return new FlipbookRoomParticipantKickedEventResponse(response.roomCode(), response.kickedUserUuid(),
            response.kickedNickname(), response.participantCount(), response.kickedAt());
    }
}
