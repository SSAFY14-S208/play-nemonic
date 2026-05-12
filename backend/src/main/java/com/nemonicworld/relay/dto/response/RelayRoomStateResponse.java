package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.support.RelayRoomTimeLimitSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Schema(description = "릴레이 방 상태 조회 응답")
/**
 * 공유 링크 진입, 새로고침, WebSocket 연결 전 화면 구성을 위한 현재 방 상태 스냅샷입니다.
 */
public record RelayRoomStateResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "WAITING") RelayRoomStatus status,
    @Schema(description = "방장 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String hostUserUuid,
    @Schema(description = "파트별 제한 시간(초)", example = "45") int timeLimitSeconds,
    @Schema(description = "현재 백오피스 설정 기준 신규 릴레이 방 기본 제한시간(초)", example = "45") int timeLimitDefaultSeconds,
    @Schema(description = "현재 백오피스 설정 기준 대기방에서 선택 가능한 제한시간 목록(초)", example = "[30,45,60]") List<Integer> timeLimitAllowedSeconds,
    @Schema(description = "최소 시작 인원", example = "2") int minParticipants,
    @Schema(description = "최대 참여 인원", example = "6") int maxParticipants,
    @Schema(description = "현재 참여자 수", example = "1") int participantCount,
    @Schema(description = "현재 진행 파트. 게임 시작 전에는 null입니다.", example = "FACE") RelayDrawingPart currentPart,
    @Schema(description = "Redis에 저장된 전체 파트 배정 개수", example = "9") int assignmentCount,
    @Schema(description = "현재 파트 시작 시각", example = "2026-05-05T12:00:00") LocalDateTime partStartedAt,
    @Schema(description = "현재 파트 마감 시각", example = "2026-05-05T12:00:45") LocalDateTime partDeadlineAt,
    @Schema(description = "게임 시작 시각", example = "2026-05-05T12:00:00") LocalDateTime gameStartedAt,
    @Schema(description = "현재 참여자 목록") List<RelayRoomParticipantResponse> participants,
    @Schema(description = "조회 요청자 기준 상태") RelayRoomViewerResponse viewer,
    @Schema(description = "방 생성 시각", example = "2026-05-04T12:00:00") LocalDateTime createdAt,
    @Schema(description = "방 수정 시각", example = "2026-05-04T12:00:00") LocalDateTime updatedAt) {

    public RelayRoomStateResponse(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, int participantCount, RelayDrawingPart currentPart,
        List<RelayRoomParticipantResponse> participants, RelayRoomViewerResponse viewer, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, RelayRoomTimeLimitSettings.defaultSettings(),
            minParticipants, maxParticipants, participantCount, currentPart, participants, viewer, createdAt,
            updatedAt);
    }

    public RelayRoomStateResponse(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        RelayRoomTimeLimitSettings timeLimitSettings, int minParticipants, int maxParticipants, int participantCount,
        RelayDrawingPart currentPart, List<RelayRoomParticipantResponse> participants, RelayRoomViewerResponse viewer,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, participantCount,
            currentPart, 0, null, null, null, participants, viewer, createdAt, updatedAt, timeLimitSettings);
    }

    public RelayRoomStateResponse(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, int participantCount, RelayDrawingPart currentPart,
        int assignmentCount, LocalDateTime partStartedAt, LocalDateTime partDeadlineAt, LocalDateTime gameStartedAt,
        List<RelayRoomParticipantResponse> participants, RelayRoomViewerResponse viewer, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, participantCount,
            currentPart, assignmentCount, partStartedAt, partDeadlineAt, gameStartedAt, participants, viewer, createdAt,
            updatedAt, RelayRoomTimeLimitSettings.defaultSettings());
    }

    private RelayRoomStateResponse(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, int participantCount, RelayDrawingPart currentPart,
        int assignmentCount, LocalDateTime partStartedAt, LocalDateTime partDeadlineAt, LocalDateTime gameStartedAt,
        List<RelayRoomParticipantResponse> participants, RelayRoomViewerResponse viewer, LocalDateTime createdAt,
        LocalDateTime updatedAt, RelayRoomTimeLimitSettings timeLimitSettings) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, timeLimitSettings.defaultSeconds(),
            timeLimitSettings.allowedSecondsList(), minParticipants, maxParticipants, participantCount, currentPart,
            assignmentCount, partStartedAt, partDeadlineAt, gameStartedAt, participants, viewer, createdAt, updatedAt);
    }

    /**
     * Redis 상태 모델에서 외부에 노출할 조회 응답 값을 구성합니다.
     */
    public static RelayRoomStateResponse from(RelayRoomState roomState, RelayRoomViewerResponse viewer) {
        return from(roomState, viewer, RelayRoomTimeLimitSettings.defaultSettings());
    }

    public static RelayRoomStateResponse from(RelayRoomState roomState, RelayRoomViewerResponse viewer,
        RelayRoomTimeLimitSettings timeLimitSettings) {
        // Redis 저장 순서가 흔들려도 대기실/게임 화면은 입장 순서 기준으로 안정적으로 표시합니다.
        List<RelayRoomParticipantResponse> participantResponses = roomState.participants().stream()
            .sorted(Comparator.comparingInt(participant -> participant.joinOrder()))
            .map(RelayRoomParticipantResponse::from).toList();

        // 이미지 객체 키나 fileId 같은 내부 진행 참조값은 상태 조회 응답에 노출하지 않습니다.
        return new RelayRoomStateResponse(roomState.roomCode(), roomState.status(), roomState.hostUserUuid(),
            roomState.timeLimitSeconds(), roomState.minParticipants(), roomState.maxParticipants(),
            roomState.participantCount(), roomState.currentPart(), roomState.assignments().size(),
            roomState.partStartedAt(), roomState.partDeadlineAt(), roomState.gameStartedAt(), participantResponses,
            viewer, roomState.createdAt(), roomState.updatedAt(), timeLimitSettings);
    }
}
