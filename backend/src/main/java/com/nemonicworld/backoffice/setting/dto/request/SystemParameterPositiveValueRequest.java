package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "양의 정수형 시스템 파라미터 설정값")
public record SystemParameterPositiveValueRequest(
    @JsonDeserialize(using = StrictIntegerDeserializer.class) @Schema(example = "10") Integer value,

    @Schema(description = "단위", example = "seconds") String unit,

    @Schema(description = "설정 설명", example = "재연결 유예 시간") String description) {
}
