package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "그리기 제한 시간 설정값")
public record SystemParameterTimeLimitRequest(
    @JsonProperty("default") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer defaultSeconds,

    @JsonDeserialize(contentUsing = StrictIntegerDeserializer.class) List<Integer> allowed,

    @Schema(description = "단위", example = "seconds") String unit,

    @Schema(description = "설정 설명", example = "릴레이 방 그리기 제한 시간") String description) {
}
