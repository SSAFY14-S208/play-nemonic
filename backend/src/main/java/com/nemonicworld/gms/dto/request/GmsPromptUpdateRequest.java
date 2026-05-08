package com.nemonicworld.gms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS 프롬프트 수정 요청")
public record GmsPromptUpdateRequest(
    @Size(max = 64) @Schema(description = "프롬프트 이름", example = "오늘의 운세 프롬프트") String name,

    @Schema(description = "프롬프트 본문") String content,

    @Pattern(regexp = "fortune|sticker") String featureType) {
}
