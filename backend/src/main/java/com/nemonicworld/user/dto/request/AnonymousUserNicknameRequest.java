package com.nemonicworld.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "익명 사용자 닉네임 설정/수정 요청")
/**
 * 익명 사용자의 닉네임을 처음 설정하거나 이후 변경할 때 사용하는 요청 DTO입니다.
 *
 * 닉네임은 문자 종류를 제한하지 않으므로 상세 검증은 서비스 계층에서 정책 기준으로 처리합니다.
 */
public record AnonymousUserNicknameRequest(
    @Schema(description = "서버가 발급한 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "설정할 닉네임", example = "망고") String nickname) {
}
