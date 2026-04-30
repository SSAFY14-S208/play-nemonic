package com.nemonicworld.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "익명 사용자 닉네임 설정/수정 응답")
public record AnonymousUserNicknameResponse(
    @Schema(description = "서버에 등록된 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "설정된 닉네임", example = "망고") String nickname,
    @Schema(description = "닉네임 수정 시각", example = "2026-04-30T14:20:00") LocalDateTime updatedAt) {
}
