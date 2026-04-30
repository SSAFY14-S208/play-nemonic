package com.nemonicworld.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "익명 사용자 UUID 검증 요청")
public record AnonymousUserVerifyRequest(
    @Schema(description = "클라이언트가 보관 중인 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid) {
}
