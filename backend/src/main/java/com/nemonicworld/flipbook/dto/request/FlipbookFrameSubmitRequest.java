package com.nemonicworld.flipbook.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플립북 프레임 제출 요청")
public record FlipbookFrameSubmitRequest(
    @Schema(description = "현재 사용자가 제출하는 플립북 번호", example = "1") Integer flipbookIndex,
    @Schema(description = "현재 사용자가 제출하는 프레임 번호", example = "2") Integer frameIndex,
    @Schema(description = "업로드 완료 확인된 file_upload ID") String fileId) {
}
