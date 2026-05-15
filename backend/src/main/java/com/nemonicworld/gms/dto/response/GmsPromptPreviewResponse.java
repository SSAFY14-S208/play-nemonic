package com.nemonicworld.gms.dto.response;

import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneDesign;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneResult;
import com.nemonicworld.fortune.dto.response.FortuneResponse.SajuInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GMS prompt preview response")
public record GmsPromptPreviewResponse(
    @Schema(description = "Previewed feature type", example = "fortune") String featureType,
    @Schema(description = "Generated fortune text result") FortuneResult fortune,
    @Schema(description = "Sample saju input used for preview") SajuInfo saju,
    @Schema(description = "Fortune card render metadata") FortuneDesign design,
    @Schema(description = "Base64 data URL for the rendered preview PNG") String previewImageBase64) {
}
