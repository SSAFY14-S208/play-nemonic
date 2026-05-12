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
    int timeLimitSeconds, int timeLimitDefaultSeconds, List<Integer> timeLimitAllowedSeconds, int minParticipants,
    int maxParticipants, int participantCount, RelayDrawingPart currentPart, int assignmentCount,
    LocalDateTime partStartedAt, LocalDateTime partDeadlineAt, LocalDateTime gameStartedAt,
    List<RelayRoomParticipantResponse> participants, RelayRoomParticipantResponse changedParticipant,
    LocalDateTime createdAt, LocalDateTime updatedAt) {

    /**
     * REST 상태 응답에서 요청자별 viewer 정보만 제외해 방 전체 이벤트 payload로 변환합니다.
     */
    public static RelayRoomEventStateResponse from(RelayRoomStateResponse roomStateResponse) {
        return from(roomStateResponse, null);
    }

    /**
     * 연결/해제처럼 특정 참여자에 의해 발생한 이벤트에서는 해당 사용자 UUID와 닉네임도 함께 담습니다.
     */
    public static RelayRoomEventStateResponse from(RelayRoomStateResponse roomStateResponse, String changedUserUuid) {
        return new RelayRoomEventStateResponse(roomStateResponse.roomCode(), roomStateResponse.status(),
            roomStateResponse.hostUserUuid(), roomStateResponse.timeLimitSeconds(),
            roomStateResponse.timeLimitDefaultSeconds(), roomStateResponse.timeLimitAllowedSeconds(),
            roomStateResponse.minParticipants(), roomStateResponse.maxParticipants(),
            roomStateResponse.participantCount(), roomStateResponse.currentPart(), roomStateResponse.assignmentCount(),
            roomStateResponse.partStartedAt(), roomStateResponse.partDeadlineAt(), roomStateResponse.gameStartedAt(),
            roomStateResponse.participants(), findChangedParticipant(roomStateResponse, changedUserUuid),
            roomStateResponse.createdAt(), roomStateResponse.updatedAt());
    }

    private static RelayRoomParticipantResponse findChangedParticipant(RelayRoomStateResponse roomStateResponse,
        String changedUserUuid) {
        if (changedUserUuid == null) {
            return null;
        }

        return roomStateResponse.participants().stream()
            .filter(participant -> changedUserUuid.equals(participant.userUuid())).findFirst().orElse(null);
    }
}
