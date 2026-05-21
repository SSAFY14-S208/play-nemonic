package com.nemonicworld.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "익명 사용자 생년월일 정보 등록/수정 응답")
/**
 * 생년월일 정보 등록/수정 API가 클라이언트에 반환하는 응답 DTO입니다.
 *
 * 운세 화면이 바로 재사용할 수 있도록 저장된 날짜, 시간, 양력/음력 여부와 수정 시각만 담습니다.
 */
public record AnonymousUserBirthInfoResponse(
    @Schema(description = "서버에 등록된 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "저장된 생년월일", example = "1998-03-15") LocalDate birthday,
    @Schema(description = "저장된 생시", example = "13:30:00") LocalTime birthtime,
    @Schema(description = "저장된 음력 여부", example = "false") Boolean isLunar,
    @Schema(description = "생년월일 정보 수정 시각", example = "2026-05-02T15:30:00") LocalDateTime updatedAt) {
}
