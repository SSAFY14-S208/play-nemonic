package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import java.time.LocalDateTime;

/**
 * 방장 퇴장으로 새 방장이 승계되었음을 방 전체 topic에 알리는 이벤트 payload입니다.
 */
public record FlipbookRoomHostChangedEventResponse(String roomCode, String previousHostUserUuid, String newHostUserUuid,
    String newHostNickname, LocalDateTime changedAt) {

    public static FlipbookRoomHostChangedEventResponse from(FlipbookRoomLeaveResponse response) {
        return new FlipbookRoomHostChangedEventResponse(response.roomCode(), response.leftUserUuid(),
            response.newHostUserUuid(), response.newHostNickname(), response.leftAt());
    }
}
