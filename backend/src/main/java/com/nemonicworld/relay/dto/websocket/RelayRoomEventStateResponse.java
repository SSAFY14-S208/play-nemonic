package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomParticipantResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 전체 topic에 노출할 릴레이 방 상태 스냅샷입니다.
 */
public record RelayRoomEventStateResponse(String roomCode, RelayRoomStatus status, String hostUserUuid,
    int timeLimitSeconds, int minParticipants, int maxParticipants, int participantCount, RelayDrawingPart currentPart,
    List<RelayRoomParticipantResponse> participants, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * REST 상태 응답에서 요청자별 viewer 정보만 제외해 방 전체 이벤트 payload로 변환합니다.
     */
    public static RelayRoomEventStateResponse from(RelayRoomStateResponse roomStateResponse) {
        return new RelayRoomEventStateResponse(roomStateResponse.roomCode(), roomStateResponse.status(),
            roomStateResponse.hostUserUuid(), roomStateResponse.timeLimitSeconds(), roomStateResponse.minParticipants(),
            roomStateResponse.maxParticipants(), roomStateResponse.participantCount(), roomStateResponse.currentPart(),
            roomStateResponse.participants(), roomStateResponse.createdAt(), roomStateResponse.updatedAt());
    }
}
