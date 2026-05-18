package com.nemonicworld.gms.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "GMS prompt preview request")
public class GmsPromptPreviewRequest {

    @NotBlank(message = "Prompt feature type is required.")
    @Pattern(regexp = "fortune|sticker", message = "Prompt feature type is invalid.")
    @Schema(description = "Prompt feature type", example = "fortune")
    private final String featureType;

    @NotBlank(message = "Prompt content is required.")
    @Schema(description = "Unsaved GMS prompt body to test")
    private final String content;

    @Valid
    @NotNull(message = "Sample saju is required.")
    @Schema(description = "Sample saju for fortune preview")
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
