package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Schema(description = "플립북 방 상태 조회 응답")
/**
 * 공유 링크 진입, 새로고침, WebSocket 연결 전 화면 구성을 위한 현재 플립북 방 상태 스냅샷입니다.
 */
public record FlipbookRoomStateResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "WAITING") FlipbookRoomStatus status,
    @Schema(description = "방장 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String hostUserUuid,
    @Schema(description = "라운드별 제한 시간(초)", example = "45") int timeLimitSeconds,
    @Schema(description = "최소 시작 인원", example = "2") int minParticipants,
    @Schema(description = "최대 참여 인원", example = "6") int maxParticipants,
    @Schema(description = "현재 참여자 수", example = "1") int participantCount,
    @Schema(description = "현재 라운드. 게임 시작 전에는 null입니다.", example = "1") Integer currentRound,
    @Schema(description = "전체 라운드 수. 게임 시작 전에는 null입니다.", example = "4") Integer totalRounds,
    @Schema(description = "현재 라운드 시작 시각", example = "2026-05-06T12:00:00") LocalDateTime roundStartedAt,
    @Schema(description = "현재 라운드 마감 시각", example = "2026-05-06T12:00:45") LocalDateTime roundDeadlineAt,
    @Schema(description = "게임 시작 시각", example = "2026-05-06T12:00:00") LocalDateTime gameStartedAt,
    @Schema(description = "현재 참여자 목록") List<FlipbookRoomParticipantResponse> participants,
    @Schema(description = "조회 요청자 기준 상태") FlipbookRoomViewerResponse viewer,
    @Schema(description = "방 생성 시각", example = "2026-05-06T12:00:00") LocalDateTime createdAt,
    @Schema(description = "방 수정 시각", example = "2026-05-06T12:00:00") LocalDateTime updatedAt) {

    public FlipbookRoomStateResponse(String roomCode, FlipbookRoomStatus status, String hostUserUuid,
        int timeLimitSeconds, int minParticipants, int maxParticipants, int participantCount,
        List<FlipbookRoomParticipantResponse> participants, FlipbookRoomViewerResponse viewer, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, participantCount, null,
            null, null, null, null, participants, viewer, createdAt, updatedAt);
    }

    /**
     * Redis 상태 모델에서 외부에 노출할 조회 응답 값을 구성합니다.
     */
    public static FlipbookRoomStateResponse from(FlipbookRoomState roomState, FlipbookRoomViewerResponse viewer) {
        // Redis 저장 순서가 흔들려도 대기실 화면은 입장 순서 기준으로 안정적으로 표시합니다.
        List<FlipbookRoomParticipantResponse> participantResponses = roomState.participants().stream()
            .sorted(Comparator.comparingInt(participant -> participant.joinOrder()))
            .map(FlipbookRoomParticipantResponse::from).toList();

        return new FlipbookRoomStateResponse(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(),
            roomState.timeLimitSeconds(), roomState.minParticipants(), roomState.maxParticipants(),
            roomState.participantCount(), roomState.currentRound(), roomState.totalRounds(), roomState.roundStartedAt(),
            roomState.roundDeadlineAt(), roomState.gameStartedAt(), participantResponses, viewer, roomState.createdAt(),
            roomState.updatedAt());
    }
}
