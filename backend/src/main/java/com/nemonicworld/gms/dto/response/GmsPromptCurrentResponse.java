package com.nemonicworld.gms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current GMS prompt response")
public record GmsPromptCurrentResponse(
    @Schema(description = "Prompt feature type", example = "fortune") String featureType,
    @Schema(description = "Current prompt source", example = "database") String source,
    @Schema(description = "Current prompt") GmsPromptResponse prompt) {
}
