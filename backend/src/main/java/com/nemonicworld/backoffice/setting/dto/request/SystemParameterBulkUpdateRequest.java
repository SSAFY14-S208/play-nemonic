package com.nemonicworld.backoffice.setting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "시스템 파라미터 일괄 수정 요청")
public record SystemParameterBulkUpdateRequest(
    @NotEmpty @Valid @Schema(description = "수정할 시스템 파라미터 항목 목록") List<SystemParameterBulkUpdateItem> items) {
}
