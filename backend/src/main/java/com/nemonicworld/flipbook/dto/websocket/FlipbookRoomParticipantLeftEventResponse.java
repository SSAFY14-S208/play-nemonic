package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import java.time.LocalDateTime;

/**
 * 대기실에서 참여자가 스스로 퇴장했음을 방 전체 topic에 알리는 이벤트 payload입니다.
 */
public record FlipbookRoomParticipantLeftEventResponse(String roomCode, String leftUserUuid, String leftNickname,
    int participantCount, LocalDateTime leftAt) {

    public static FlipbookRoomParticipantLeftEventResponse from(FlipbookRoomLeaveResponse response) {
        return new FlipbookRoomParticipantLeftEventResponse(response.roomCode(), response.leftUserUuid(),
            response.leftNickname(), response.participantCount(), response.leftAt());
    }
}
