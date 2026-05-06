package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 릴레이 최종 결과 화면에서 파트별 작성자 표시를 위해 사용하는 메타데이터입니다.
 */
@Schema(description = "릴레이 결과 파트 작성자 정보")
public record RelayRoomResultPartResponse(@Schema(description = "릴레이 파트", example = "FACE") RelayDrawingPart part,
    @Schema(description = "파트 작성자 UUID", example = "11111111-1111-1111-1111-111111111111") String drawerUserUuid,
    @Schema(description = "파트 작성자 닉네임", example = "민수") String drawerNickname) {
}
