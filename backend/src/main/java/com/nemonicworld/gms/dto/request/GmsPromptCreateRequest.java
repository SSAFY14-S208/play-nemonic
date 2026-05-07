package com.nemonicworld.gms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS 프롬프트 생성 요청")
public record GmsPromptCreateRequest(
    @NotBlank @Size(max = 64) @Schema(description = "프롬프트 이름", example = "오늘의 운세 프롬프트") String name,

    @NotBlank @Schema(description = "프롬프트 본문") String content,

    @NotBlank @Pattern(regexp = "fortune|sticker") String featureType) {
}
