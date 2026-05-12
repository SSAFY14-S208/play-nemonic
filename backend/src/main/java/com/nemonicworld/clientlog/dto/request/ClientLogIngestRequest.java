package com.nemonicworld.clientlog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "클라이언트 로그 이벤트 수집 요청")
public record ClientLogIngestRequest(
    @NotNull @Size(max = 200) @Schema(description = "표준 로그 이벤트 목록") List<Map<String, Object>> events) {
}
