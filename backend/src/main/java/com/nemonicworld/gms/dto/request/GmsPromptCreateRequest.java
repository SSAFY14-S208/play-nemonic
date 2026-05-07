package com.nemonicworld.gms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS prompt creation request")
public record GmsPromptCreateRequest(
    @NotBlank @Size(max = 64) @Schema(description = "Prompt name", example = "Daily fortune prompt") String name,

    @NotBlank @Schema(description = "Prompt body") String content,

    @NotBlank @Pattern(regexp = "fortune|sticker") String featureType) {
}
