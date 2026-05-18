package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS 프롬프트 수정 요청")
public class GmsPromptUpdateRequest {

    @Size(max = 64, message = "프롬프트 이름은 64자 이하여야 합니다.")
    @Schema(description = "프롬프트 이름", example = "오늘의 운세 프롬프트")
    private final String name;

    @Schema(description = "프롬프트 본문. 미전달 시 기존 값을 유지합니다.", example = "운세 결과에 postitLine을 포함한다.")
    private final String content;

    @Schema(description = "기능 타입. 활성 프롬프트는 변경할 수 없습니다.", example = "fortune")
    @Pattern(regexp = "fortune|sticker", message = "프롬프트 기능 타입이 올바르지 않습니다.")
    private final String featureType;

    @JsonCreator
    public GmsPromptUpdateRequest(@JsonProperty("name") String name, @JsonProperty("content") String content,
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
