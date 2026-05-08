package com.nemonicworld.fortune.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record FortuneAvailabilityResponse(@Schema(description = "오늘의 운세 생성 가능 여부", example = "false") boolean available,
    @Schema(description = "운세 생성 기준 날짜(KST)", example = "2026-05-08") LocalDate fortuneDate,
    @Schema(description = "오늘 이미 생성한 운세 ID", example = "7f5c2f3e-1234-5678-9abc-0f12a3456789") String todayFortuneId,
    @Schema(description = "오늘 운세 생성 시각", example = "2026-05-08T10:15:30") LocalDateTime createdAt,
    @Schema(description = "다음 생성 가능 시각(KST)", example = "2026-05-09T00:00:00+09:00") OffsetDateTime nextAvailableAt) {
}
