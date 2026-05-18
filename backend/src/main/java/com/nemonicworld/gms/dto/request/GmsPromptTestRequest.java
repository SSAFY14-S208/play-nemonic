package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "저장된 GMS 프롬프트 테스트 요청")
public class GmsPromptTestRequest {

    @Valid
    @NotNull(message = "Prompt test sample saju is required.")
    @Schema(description = "저장된 프롬프트 테스트에 사용할 샘플 만세력/사주 정보")
    private final FortuneCreateRequest sampleSaju;

    @JsonCreator
    public GmsPromptTestRequest(@JsonProperty("sampleSaju") FortuneCreateRequest sampleSaju) {
        this.sampleSaju = sampleSaju;
    }

    public FortuneCreateRequest sampleSaju() {
        return sampleSaju;
    }
}
