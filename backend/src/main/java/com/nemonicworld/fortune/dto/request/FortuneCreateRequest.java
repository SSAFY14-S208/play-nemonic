package com.nemonicworld.fortune.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘의 운세 생성 요청")
public record FortuneCreateRequest(@Schema(description = "달력 타입", example = "solar") String calendarType,
    @Schema(description = "년주", example = "임신") String yearPillar,
    @Schema(description = "월주", example = "경술") String monthPillar,
    @Schema(description = "일주", example = "계유") String dayPillar,
    @Schema(description = "시주", example = "을묘", nullable = true) String hourPillar,
    @Schema(description = "일간 오행", example = "수") String dayMasterElement,
    @Schema(description = "일지 오행", example = "금") String dayBranchElement,
    @Schema(description = "일간 음양", example = "음") String dayMasterYinYang,
    @Schema(description = "일지 음양", example = "음") String dayBranchYinYang) {
}
