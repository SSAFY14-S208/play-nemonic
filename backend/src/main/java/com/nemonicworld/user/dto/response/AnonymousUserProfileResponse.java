package com.nemonicworld.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "익명 사용자 프로필 조회 응답")
/**
 * 익명 사용자 프로필 조회 API가 클라이언트에 반환하는 응답 DTO입니다.
 *
 * 운세 기능에서 재사용하는 생년월일, 생시, 양력/음력 여부는 아직 입력되지 않았으면 null로 반환합니다.
 */
public record AnonymousUserProfileResponse(
    @Schema(description = "서버에 등록된 익명 사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String userUuid,
    @Schema(description = "익명 사용자 닉네임", example = "망고") String nickname,
    @Schema(description = "운세 입력 재사용용 생년월일", example = "1998-03-15", nullable = true) LocalDate birthday,
    @Schema(description = "운세 입력 재사용용 생시", example = "13:30:00", nullable = true) LocalTime birthtime,
    @Schema(description = "운세 입력 재사용용 음력 여부", example = "false", nullable = true) Boolean isLunar,
    @Schema(description = "사용자 등록 시각", example = "2026-04-30T12:34:56") LocalDateTime createdAt,
    @Schema(description = "사용자 정보 수정 시각", example = "2026-04-30T14:20:00") LocalDateTime updatedAt,
    @Schema(description = "최근 방문 시각", example = "2026-04-30T13:50:00") LocalDateTime lastSeenAt) {
}
