package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "방 참여 인원 제한 설정값")
public record SystemParameterParticipantLimitRequest(
    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "3") Integer min,

    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "8") Integer max,

    @Schema(description = "단위", example = "people") String unit,

    @Schema(description = "설정 설명", example = "릴레이 방 참여 인원 제한") String description) {
}
