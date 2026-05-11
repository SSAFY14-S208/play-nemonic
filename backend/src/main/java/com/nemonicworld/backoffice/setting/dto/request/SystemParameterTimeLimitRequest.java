package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Drawing time limit setting value")
public record SystemParameterTimeLimitRequest(
    @JsonProperty("default") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer defaultSeconds,

    @JsonDeserialize(contentUsing = StrictIntegerDeserializer.class) List<Integer> allowed,

    @Schema(description = "Unit label", example = "seconds") String unit,

    @Schema(description = "Setting description", example = "Relay room drawing time limit") String description) {
}
