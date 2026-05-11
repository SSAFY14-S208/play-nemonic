package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Participant limit setting value")
public record SystemParameterParticipantLimitRequest(
    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "3") Integer min,

    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "8") Integer max,

    @Schema(description = "Unit label", example = "people") String unit,

    @Schema(description = "Setting description", example = "Relay room participant limit") String description) {
}
