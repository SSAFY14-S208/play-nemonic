package com.nemonicworld.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "익명 사용자 생년월일 정보 등록/수정 요청")
/**
 * 운세 기능에서 재사용할 생년월일, 생시, 양력/음력 여부를 등록하거나 수정할 때 사용하는 요청 DTO입니다.
 *
 * 날짜와 시간 문자열은 서비스 계층에서 정책에 맞게 파싱하고 동일한 오류 메시지로 변환합니다.
 */
public record AnonymousUserBirthInfoRequest(
    @Schema(description = "서버가 발급한 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "생년월일", example = "1998-03-15") String birthday,
    @Schema(description = "생시", example = "13:30:00") String birthtime,
    @Schema(description = "음력 여부", example = "false") Boolean isLunar) {
}
