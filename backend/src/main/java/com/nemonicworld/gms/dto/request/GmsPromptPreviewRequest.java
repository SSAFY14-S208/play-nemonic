package com.nemonicworld.gms.dto.request;

import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "GMS prompt preview request")
public record GmsPromptPreviewRequest(
    @NotBlank @Pattern(regexp = "fortune|sticker") @Schema(description = "Preview target feature type", example = "fortune") String featureType,

    @NotBlank @Schema(description = "Candidate GMS prompt template content") String content,

    @Valid @NotNull @Schema(description = "Sample saju input used for fortune preview") FortuneCreateRequest sampleSaju) {
}
