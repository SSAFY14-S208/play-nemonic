package com.nemonicworld.infinitecanvas.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "무한 캔버스 퇴장 응답")
public record InfiniteCanvasLeaveResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "퇴장한 사용자 UUID") String userUuid, @Schema(description = "퇴장한 사용자 닉네임") String nickname,
    @Schema(description = "퇴장 후 현재 참여자 수") int participantCount,
    @Schema(description = "방장 승계 발생 여부") boolean hostChanged,
    @Schema(description = "새 방장 UUID. 승계가 없으면 null입니다.", nullable = true) String newHostUserUuid,
    @Schema(description = "새 방장 닉네임. 승계가 없으면 null입니다.", nullable = true) String newHostNickname,
    @Schema(description = "마지막 참여자 퇴장으로 캔버스가 종료되었는지") boolean closed,
    @Schema(description = "종료 시각", nullable = true) LocalDateTime closedAt) {
}
