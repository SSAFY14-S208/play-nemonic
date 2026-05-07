package com.nemonicworld.files.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "파일 삭제 응답")
public record FileDeleteResponse(
    @Schema(description = "파일 아이디", example = "550e8400-e29b-41d4-a716-446655440000") String fileId,
    @Schema(description = "업로드 상태", example = "DELETED") String status) {
}
