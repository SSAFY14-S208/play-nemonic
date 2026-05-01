package com.nemonicworld.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "익명 사용자 UUID 검증 응답")
public record AnonymousUserVerifyResponse(
    @Schema(description = "서버에 등록된 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "익명 사용자 닉네임", example = "익명") String nickname,
    @Schema(description = "최근 방문 시각", example = "2026-04-30T13:50:00") LocalDateTime lastSeenAt) {
}
