package com.nemonicworld.gms.dto.request;

import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "GMS 프롬프트 미리보기 요청")
public record GmsPromptPreviewRequest(
    @NotBlank(message = "프롬프트 기능 타입을 입력해야 합니다.") @Pattern(regexp = "fortune|sticker", message = "프롬프트 기능 타입이 올바르지 않습니다.") @Schema(description = "미리보기 대상 기능 타입", example = "fortune") String featureType,

    @NotBlank(message = "프롬프트 본문을 입력해야 합니다.") @Schema(description = "저장 전 테스트할 GMS 프롬프트 템플릿 본문") String content,

    @Valid @NotNull(message = "미리보기에 사용할 샘플 사주 정보를 입력해야 합니다.") @Schema(description = "운세 미리보기에 사용할 샘플 사주 정보") FortuneCreateRequest sampleSaju) {
}
