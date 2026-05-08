package com.nemonicworld.fortune.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "오늘의 운세 생성 응답")
public record FortuneCreateResponse(@Schema(description = "운세 산출물 ID") String fortuneId,
    @Schema(description = "KST 기준 운세 날짜", example = "2026-04-29") LocalDate date,
    @Schema(description = "운세 제목", example = "오늘은 흐름을 정리하는 날") String title,
    @Schema(description = "운세 요약") String summary, @Schema(description = "종합운", example = "78") int overallLuck,
    @Schema(description = "애정운", example = "66") int loveLuck,
    @Schema(description = "일/학업운", example = "84") int workLuck,
    @Schema(description = "금전운", example = "71") int moneyLuck,
    @Schema(description = "행운의 색", example = "은회색") String luckyColor,
    @Schema(description = "행운의 키워드", example = "정리") String luckyKeyword,
    @Schema(description = "주의할 점", nullable = true) String caution,
    @Schema(description = "네모닉 출력용 한 줄 요약", example = "오늘은 정리할수록 운이 열린다") String postitLine) {
}
