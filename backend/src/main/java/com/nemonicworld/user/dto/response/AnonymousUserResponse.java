package com.nemonicworld.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "익명 사용자 UUID 발급 응답")
/**
 * 익명 사용자 UUID 발급 API가 클라이언트에 반환하는 응답 DTO입니다.
 *
 * 엔티티를 직접 노출하지 않고 API 계약에 필요한 값만 담습니다.
 */
public record AnonymousUserResponse(
        @Schema(description = "서버가 발급한 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
        @Schema(description = "익명 사용자 기본 닉네임", example = "익명") String nickname,
        @Schema(description = "사용자 등록 시각", example = "2026-04-30T12:34:56") LocalDateTime createdAt) {
}
