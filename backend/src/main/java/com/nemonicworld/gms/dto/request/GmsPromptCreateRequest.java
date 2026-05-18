package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS 프롬프트 생성 요청")
public class GmsPromptCreateRequest {

    @NotBlank(message = "프롬프트 이름을 입력해야 합니다.")
    @Size(max = 64, message = "프롬프트 이름은 64자 이하여야 합니다.")
    @Schema(description = "프롬프트 이름", example = "오늘의 운세 프롬프트")
    private final String name;

    @Schema(description = "GMS 호출에 사용할 프롬프트 본문", example = "만세력 결과를 바탕으로 운세를 생성한다.")
    @NotBlank(message = "프롬프트 본문을 입력해야 합니다.")
    private final String content;

    @Schema(description = "기능 타입", example = "fortune")
    @NotBlank(message = "프롬프트 기능 타입을 입력해야 합니다.")
    @Pattern(regexp = "fortune|sticker", message = "프롬프트 기능 타입이 올바르지 않습니다.")
    private final String featureType;

    @JsonCreator
    public GmsPromptCreateRequest(@JsonProperty("name") String name, @JsonProperty("content") String content,
        @JsonProperty("featureType") String featureType) {
        this.name = name;
        this.content = content;
        this.featureType = featureType;
    }

    public String name() {
        return name;
    }

    public String content() {
        return content;
    }

    public String featureType() {
        return featureType;
    }
}
