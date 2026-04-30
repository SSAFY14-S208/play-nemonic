package com.nemonicworld.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "익명 사용자 닉네임 설정/수정 요청")
public record AnonymousUserNicknameRequest(
    @Schema(description = "서버가 발급한 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "설정할 닉네임", example = "망고") String nickname) {
}
