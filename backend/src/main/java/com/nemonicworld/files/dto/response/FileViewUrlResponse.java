package com.nemonicworld.files.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "파일 조회 사전 서명 URL 발급 응답")
public record FileViewUrlResponse(
    @Schema(description = "파일 아이디", example = "550e8400-e29b-41d4-a716-446655440000") String fileId,
    @Schema(description = "MinIO GET 조회용 사전 서명 URL") String viewUrl,
    @Schema(description = "사전 서명 URL 만료 시간(초)", example = "86400") long expiresIn) {
}
