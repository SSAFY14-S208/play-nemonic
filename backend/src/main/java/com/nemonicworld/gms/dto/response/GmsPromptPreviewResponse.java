package com.nemonicworld.gms.dto.response;

import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneDesign;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneResult;
import com.nemonicworld.fortune.dto.response.FortuneResponse.SajuInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GMS 프롬프트 미리보기 응답")
public record GmsPromptPreviewResponse(@Schema(description = "미리보기 대상 기능 타입", example = "fortune") String featureType,
    @Schema(description = "후보 프롬프트로 생성한 운세 결과") FortuneResult fortune,
    @Schema(description = "미리보기에 사용한 샘플 사주 정보") SajuInfo saju,
    @Schema(description = "운세 카드 렌더링 디자인 정보") FortuneDesign design,
    @Schema(description = "렌더링된 미리보기 PNG의 Base64 데이터 URL") String previewImageBase64) {
}
