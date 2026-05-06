package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import java.time.LocalDateTime;

/**
 * 방장 퇴장으로 새 방장이 승계되었음을 방 전체 topic에 알리는 이벤트 payload입니다.
 */
public record RelayRoomHostChangedEventResponse(String roomCode, String previousHostUserUuid, String newHostUserUuid,
    String newHostNickname, LocalDateTime changedAt) {

    public static RelayRoomHostChangedEventResponse from(RelayRoomLeaveResponse response) {
        return new RelayRoomHostChangedEventResponse(response.roomCode(), response.leftUserUuid(),
            response.newHostUserUuid(), response.newHostNickname(), response.leftAt());
    }
}
