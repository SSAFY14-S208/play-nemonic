package com.nemonicworld.backoffice.setting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "시스템 파라미터 목록 응답")
public record SystemParameterListResponse(List<SystemParameterResponse> items, long totalElements) {

    public SystemParameterListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
