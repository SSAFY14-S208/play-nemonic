package com.nemonicworld.files.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "파일 업로드 사전 서명 URL 발급 응답")
public record FilePresignResponse(
    @Schema(description = "서버가 추적하는 파일 업로드 UUID", example = "8d25f3a5-3c5a-4f21-9f54-68fa4a402011") String fileId,
    @Schema(description = "MinIO PUT 업로드용 사전 서명 URL") String presignedUrl,
    @Schema(description = "사전 서명 URL 만료 시간(초)", example = "600") long expiresIn) {
}
