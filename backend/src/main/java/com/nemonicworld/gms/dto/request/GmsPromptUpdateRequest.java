package com.nemonicworld.gms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GMS prompt update request")
public record GmsPromptUpdateRequest(
    @Size(max = 64) @Schema(description = "Prompt name", example = "Daily fortune prompt") String name,

    @Schema(description = "Prompt body") String content,

    @Pattern(regexp = "fortune|sticker") String featureType) {
}
