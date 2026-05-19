package com.nemonicworld.fortune.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "오늘의 운세 응답")
public record FortuneResponse(@Schema(description = "운세 산출물 ID") String fortuneId,
    @Schema(description = "KST 기준 운세 날짜", example = "2026-04-29") LocalDate date,
    @Schema(description = "운세 결과 본문") FortuneResult fortune, @Schema(description = "요청으로 전달된 사주 정보") SajuInfo saju,
    @Schema(description = "프론트 카드 렌더링용 디자인 메타데이터") FortuneDesign design) {

    @Schema(description = "운세 결과 본문")
    public record FortuneResult(@Schema(description = "운세 제목", example = "오늘은 흐름을 정리하는 날") String title,
        @Schema(description = "운세 요약") String summary, @Schema(description = "종합운", example = "78") int overallLuck,
        @Schema(description = "애정운", example = "66") int loveLuck,
        @Schema(description = "일/학업운", example = "84") int workLuck,
        @Schema(description = "금전운", example = "71") int moneyLuck,
        @Schema(description = "행운의 색", example = "은회색") String luckyColor,
        @Schema(description = "행운의 키워드", example = "정리") String luckyKeyword,
        @Schema(description = "행운의 방향", example = "동쪽") String luckyDirection,
        @Schema(description = "주의할 점", nullable = true) String caution,
        @Schema(description = "네모닉 출력용 한 줄 요약", example = "오늘은 정리할수록 운이 열린다") String postitLine) {
    }

    @Schema(description = "요청으로 전달된 사주 정보")
    public record SajuInfo(@Schema(description = "달력 타입", example = "solar") String calendarType,
        @Schema(description = "년주", example = "임신") String yearPillar,
        @Schema(description = "월주", example = "경술") String monthPillar,
        @Schema(description = "일주", example = "계유") String dayPillar,
        @Schema(description = "시주", example = "을묘", nullable = true) String hourPillar,
        @Schema(description = "일간 오행", example = "수") String dayMasterElement,
        @Schema(description = "일지 오행", example = "금") String dayBranchElement,
        @Schema(description = "일간 음양", example = "음") String dayMasterYinYang,
        @Schema(description = "일지 음양", example = "음") String dayBranchYinYang) {
    }

    @Schema(description = "프론트 카드 렌더링용 디자인 메타데이터")
    public record FortuneDesign(@Schema(description = "카드 테마 키", example = "moon", nullable = true) String cardTheme,
        @Schema(description = "배경색", example = "#2C2C4A", nullable = true) String bgColor,
        @Schema(description = "강조색", example = "#C0C0C0", nullable = true) String accentColor,
        @Schema(description = "아이콘 키", example = "moon_waning", nullable = true) String iconKey) {
    }
}
