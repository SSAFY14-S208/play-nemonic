package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "시스템 파라미터 일괄 수정 항목")
public record SystemParameterBulkUpdateItem(
    @NotNull @Positive @Schema(description = "시스템 파라미터 ID", example = "10") Long id,

    @NotNull @Schema(description = "JSON 형태의 파라미터 값. 객체/배열/원시값 모두 허용") JsonNode value) {
}
