package com.nemonicworld.flipbook.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플립북 방 설정 변경 요청")
/**
 * 대기실에서 방장이 변경할 수 있는 플립북 방 설정 요청입니다.
 *
 * 제한 시간 허용값은 서비스 계층에서 동일한 오류 메시지로 검증합니다.
 */
public record FlipbookRoomSettingsRequest(
    @Schema(description = "라운드별 제한 시간(초)", example = "45") Integer timeLimitSeconds) {
}
