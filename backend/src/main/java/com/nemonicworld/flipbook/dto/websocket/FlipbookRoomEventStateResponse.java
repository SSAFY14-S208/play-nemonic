package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 전체 topic에 노출할 플립북 방 상태 스냅샷입니다.
 */
public record FlipbookRoomEventStateResponse(String roomCode, FlipbookRoomStatus status, String hostUserUuid,
    int timeLimitSeconds, int minParticipants, int maxParticipants, int participantCount,
    List<FlipbookRoomParticipantResponse> participants, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * REST 상태 응답에서 요청자별 viewer 정보만 제외해 방 전체 이벤트 payload로 변환합니다.
     */
    public static FlipbookRoomEventStateResponse from(FlipbookRoomStateResponse roomStateResponse) {
        return new FlipbookRoomEventStateResponse(roomStateResponse.roomCode(), roomStateResponse.status(),
            roomStateResponse.hostUserUuid(), roomStateResponse.timeLimitSeconds(), roomStateResponse.minParticipants(),
            roomStateResponse.maxParticipants(), roomStateResponse.participantCount(), roomStateResponse.participants(),
            roomStateResponse.createdAt(), roomStateResponse.updatedAt());
    }
}
