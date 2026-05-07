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
    List<FlipbookRoomParticipantResponse> participants, FlipbookRoomParticipantResponse changedParticipant,
    LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * REST 상태 응답에서 요청자별 viewer 정보만 제외해 방 전체 이벤트 payload로 변환합니다.
     */
    public static FlipbookRoomEventStateResponse from(FlipbookRoomStateResponse roomStateResponse) {
        return from(roomStateResponse, null);
    }

    /**
     * 연결/해제처럼 특정 참여자에 의해 발생한 이벤트에서는 해당 사용자 UUID와 닉네임도 함께 담습니다.
     */
    public static FlipbookRoomEventStateResponse from(FlipbookRoomStateResponse roomStateResponse,
        String changedUserUuid) {
        return new FlipbookRoomEventStateResponse(roomStateResponse.roomCode(), roomStateResponse.status(),
            roomStateResponse.hostUserUuid(), roomStateResponse.timeLimitSeconds(), roomStateResponse.minParticipants(),
            roomStateResponse.maxParticipants(), roomStateResponse.participantCount(), roomStateResponse.participants(),
            findChangedParticipant(roomStateResponse, changedUserUuid), roomStateResponse.createdAt(),
            roomStateResponse.updatedAt());
    }

    private static FlipbookRoomParticipantResponse findChangedParticipant(FlipbookRoomStateResponse roomStateResponse,
        String changedUserUuid) {
        if (changedUserUuid == null) {
            return null;
        }

        return roomStateResponse.participants().stream()
            .filter(participant -> changedUserUuid.equals(participant.userUuid())).findFirst().orElse(null);
    }
}
