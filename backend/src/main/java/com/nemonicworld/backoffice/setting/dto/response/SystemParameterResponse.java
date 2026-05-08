package com.nemonicworld.backoffice.setting.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "시스템 파라미터 응답")
public record SystemParameterResponse(Long id, String key, JsonNode value, SystemParameterUpdatedByResponse updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static SystemParameterResponse from(SystemParameter parameter, JsonNode value) {
        SystemParameterUpdatedByResponse updatedBy = parameter.updatedById() == null || parameter.updatedById() <= 0
            || parameter.updatedByNickname() == null
                ? null
                : new SystemParameterUpdatedByResponse(parameter.updatedById(), parameter.updatedByNickname());

        return new SystemParameterResponse(parameter.id(), parameter.key(), value, updatedBy, parameter.createdAt(),
            parameter.updatedAt());
    }
}
