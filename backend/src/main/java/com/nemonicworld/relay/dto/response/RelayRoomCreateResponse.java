package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "릴레이 방 생성 응답")
public record RelayRoomCreateResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "WAITING") RelayRoomStatus status,
    @Schema(description = "방장 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String hostUserUuid,
    @Schema(description = "파트별 제한 시간(초)", example = "60") int timeLimitSeconds,
    @Schema(description = "최소 시작 인원", example = "2") int minParticipants,
    @Schema(description = "최대 참여 인원", example = "6") int maxParticipants,
    @Schema(description = "현재 참여자 수", example = "1") int participantCount,
    @Schema(description = "현재 참여자 목록") List<RelayRoomParticipantResponse> participants,
    @Schema(description = "방 생성 시각", example = "2026-05-04T12:00:00") LocalDateTime createdAt) {

    public static RelayRoomCreateResponse from(RelayRoomState roomState) {
        List<RelayRoomParticipantResponse> participantResponses = roomState.participants().stream()
            .map(RelayRoomParticipantResponse::from).toList();

        return new RelayRoomCreateResponse(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(),
            roomState.timeLimitSeconds(), roomState.minParticipants(), roomState.maxParticipants(),
            roomState.participantCount(), participantResponses, roomState.createdAt());
    }
}
