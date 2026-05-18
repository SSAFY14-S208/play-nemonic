package com.nemonicworld.gms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 GMS 프롬프트 조회 응답")
public class GmsPromptCurrentResponse {

    @Schema(description = "조회한 기능 타입", example = "fortune")
    private final String featureType;

    @Schema(description = "현재 프롬프트 출처입니다. database는 저장된 활성 프롬프트, default는 서버 기본 프롬프트를 의미합니다.", example = "database", allowableValues = {
        "database", "default"})
    private final String source;

    @Schema(description = "현재 서비스에서 사용 중인 프롬프트")
    private final GmsPromptResponse prompt;

    public GmsPromptCurrentResponse(String featureType, String source, GmsPromptResponse prompt) {
        this.featureType = featureType;
        this.source = source;
        this.prompt = prompt;
    }

    public String getFeatureType() {
        return featureType;
    }

    public String getSource() {
        return source;
    }

    public GmsPromptResponse getPrompt() {
        return prompt;
    }
}
