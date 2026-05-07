package com.nemonicworld.gms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "GMS prompt list response")
public record GmsPromptListResponse(@Schema(description = "Prompt list") List<GmsPromptResponse> items,
    @Schema(description = "Current page", example = "0") int page,
    @Schema(description = "Page size", example = "20") int size,
    @Schema(description = "Total elements", example = "1") long totalElements,
    @Schema(description = "Whether next page exists", example = "false") boolean hasNext) {
}
