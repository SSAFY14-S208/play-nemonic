package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Positive integer system parameter value")
public record SystemParameterPositiveValueRequest(
    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "10") Integer value,

    @Schema(description = "Unit label", example = "seconds") String unit,

    @Schema(description = "Setting description", example = "Reconnect grace period") String description) {
}
