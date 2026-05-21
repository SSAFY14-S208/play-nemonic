package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "플립북 방 생성 응답")
public record FlipbookRoomCreateResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "WAITING") FlipbookRoomStatus status,
    @Schema(description = "방장 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String hostUserUuid,
    @Schema(description = "라운드별 제한 시간(초)", example = "45") int timeLimitSeconds,
    @Schema(description = "라운드별 제한 시간 선택지") FlipbookRoomTimeLimitOptionsResponse timeLimitSecondsOptions,
    @Schema(description = "최소 시작 인원", example = "2") int minParticipants,
    @Schema(description = "최대 참여 인원", example = "6") int maxParticipants,
    @Schema(description = "현재 참여자 수", example = "1") int participantCount,
    @Schema(description = "현재 참여자 목록") List<FlipbookRoomParticipantResponse> participants,
    @Schema(description = "방 생성 시각", example = "2026-05-06T12:00:00") LocalDateTime createdAt) {

    /**
     * Redis에 저장한 내부 방 상태에서 API 응답에 필요한 값만 추려 변환합니다.
     */
    public static FlipbookRoomCreateResponse from(FlipbookRoomState roomState) {
        return from(roomState, null);
    }

    public static FlipbookRoomCreateResponse from(FlipbookRoomState roomState,
        FlipbookRoomTimeLimitOptionsResponse timeLimitSecondsOptions) {
        List<FlipbookRoomParticipantResponse> participantResponses = roomState.participants().stream()
            .map(FlipbookRoomParticipantResponse::from).toList();

        return new FlipbookRoomCreateResponse(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(),
            roomState.timeLimitSeconds(), timeLimitSecondsOptions, roomState.minParticipants(),
            roomState.maxParticipants(), roomState.participantCount(), participantResponses, roomState.createdAt());
    }
}
