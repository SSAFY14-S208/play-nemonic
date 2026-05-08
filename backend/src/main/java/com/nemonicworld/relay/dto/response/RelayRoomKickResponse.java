package com.nemonicworld.relay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 방 참여자 강퇴 응답")
public record RelayRoomKickResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "강퇴된 사용자 UUID", example = "11111111-1111-1111-1111-111111111111") String kickedUserUuid,
    @Schema(description = "강퇴된 사용자 닉네임", example = "망고") String kickedNickname,
    @Schema(description = "강퇴 후 현재 참여자 수", example = "2") int participantCount,
    @Schema(description = "강퇴 처리 시각", example = "2026-05-06T16:00:00") LocalDateTime kickedAt) {
}
