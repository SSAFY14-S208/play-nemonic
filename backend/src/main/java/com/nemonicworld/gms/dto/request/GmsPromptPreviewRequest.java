package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "GMS 프롬프트 미리보기 요청")
public class GmsPromptPreviewRequest {

    @NotBlank(message = "Prompt feature type is required.")
    @Pattern(regexp = "fortune|sticker", message = "Prompt feature type is invalid.")
    @Schema(description = "미리보기 대상 기능 타입. 현재 fortune 미리보기를 지원합니다.", example = "fortune")
    private final String featureType;

    @NotBlank(message = "Prompt content is required.")
    @Schema(description = "저장 전 테스트할 후보 프롬프트 본문", example = "샘플 사주를 바탕으로 오늘의 운세를 생성한다.")
    private final String content;

    @Valid
    @NotNull(message = "Sample saju is required.")
    @Schema(description = "미리보기에 사용할 샘플 만세력/사주 정보")
    private final FortuneCreateRequest sampleSaju;

    @JsonCreator
    public GmsPromptPreviewRequest(@JsonProperty("featureType") String featureType,
        @JsonProperty("content") String content, @JsonProperty("sampleSaju") FortuneCreateRequest sampleSaju) {
        this.featureType = featureType;
        this.content = content;
        this.sampleSaju = sampleSaju;
    }

    public String featureType() {
        return featureType;
    }

    public String content() {
        return content;
    }

    public FortuneCreateRequest sampleSaju() {
        return sampleSaju;
    }
}
