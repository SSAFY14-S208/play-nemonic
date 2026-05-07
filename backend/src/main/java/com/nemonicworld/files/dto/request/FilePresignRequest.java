package com.nemonicworld.files.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "파일 업로드 Presigned URL 발급 요청")
public record FilePresignRequest(
    @Schema(description = "확장자를 포함한 원본 파일명", example = "drawing.png") @NotBlank String fileName,
    @Schema(description = "업로드할 이미지 MIME 타입", example = "image/png") @NotBlank String contentType,
    @Schema(description = "파일 사용 목적. 대문자 enum 값만 허용됩니다.", example = "FLIPBOOK") @NotBlank String purpose,
    @Schema(description = "업로드할 파일 크기(bytes)", example = "1048576") Long byteSize) {
}
