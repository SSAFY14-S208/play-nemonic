package com.nemonicworld.fortune.service.gms;

/**
 * GMS 운세 생성 결과를 서버 저장 형식으로 정규화한 모델입니다.
 */
public record FortuneGmsResult(String title, String summary, int overallLuck, int loveLuck, int workLuck, int moneyLuck,
    String luckyColor, String luckyKeyword, String caution, String postitLine, String cardTheme, String bgColor,
    String accentColor, String iconKey) {
}
