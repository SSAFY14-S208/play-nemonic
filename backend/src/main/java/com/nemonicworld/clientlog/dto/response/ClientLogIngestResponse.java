package com.nemonicworld.clientlog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "클라이언트 로그 이벤트 수집 응답")
public record ClientLogIngestResponse(@Schema(description = "정상 적재 이벤트 수") int acceptedCount,
    @Schema(description = "drop 이벤트 수") int droppedCount,
    @Schema(description = "시스템 로그로 라우팅된 이벤트 수") int systemRoutedCount) {
}
