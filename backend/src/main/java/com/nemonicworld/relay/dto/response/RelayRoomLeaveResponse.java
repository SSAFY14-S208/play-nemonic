package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 방 자발적 퇴장 응답")
public record RelayRoomLeaveResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "퇴장한 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String leftUserUuid,
    @Schema(description = "퇴장한 사용자 닉네임", example = "망고") String leftNickname,
    @Schema(description = "퇴장 후 현재 참여자 수", example = "2") int participantCount,
    @Schema(description = "방장 승계 발생 여부", example = "false") boolean hostChanged,
    @Schema(description = "새 방장 사용자 UUID. 승계가 없으면 null입니다.", example = "11111111-1111-1111-1111-111111111111") String newHostUserUuid,
    @Schema(description = "새 방장 닉네임. 승계가 없으면 null입니다.", example = "포도") String newHostNickname,
    @Schema(description = "마지막 참여자 퇴장으로 방이 닫혔는지 여부", example = "false") boolean roomClosed,
    @Schema(description = "퇴장 후 방 상태", example = "WAITING") RelayRoomStatus roomStatus,
    @Schema(description = "퇴장 처리 시각", example = "2026-05-06T16:30:00") LocalDateTime leftAt) {
}
