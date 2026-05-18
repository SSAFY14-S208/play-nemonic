package com.nemonicworld.gms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS 프롬프트 생성 요청")
public record GmsPromptCreateRequest(
    @NotBlank @Size(max = 64) @Schema(description = "프롬프트 이름", example = "오늘의 운세 프롬프트") String name,

    @Schema(description = "GMS 호출에 사용할 프롬프트 본문", example = "만세력 결과를 바탕으로 운세를 생성한다.") @NotBlank String content,

    @Schema(description = "기능 타입", example = "fortune") @NotBlank @Pattern(regexp = "fortune|sticker") String featureType) {
}
