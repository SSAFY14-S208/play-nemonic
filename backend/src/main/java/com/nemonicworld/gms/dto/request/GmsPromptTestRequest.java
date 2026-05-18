package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Saved GMS prompt test request")
public class GmsPromptTestRequest {

    @Valid
    @NotNull(message = "Prompt test sample saju is required.")
    @Schema(description = "Sample saju for prompt testing")
    private final FortuneCreateRequest sampleSaju;

    @JsonCreator
    public GmsPromptTestRequest(@JsonProperty("sampleSaju") FortuneCreateRequest sampleSaju) {
        this.sampleSaju = sampleSaju;
    }

    public FortuneCreateRequest sampleSaju() {
        return sampleSaju;
    }
}
