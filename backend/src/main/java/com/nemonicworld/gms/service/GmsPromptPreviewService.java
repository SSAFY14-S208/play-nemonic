package com.nemonicworld.gms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.fortune.service.FortuneGenerationService;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.gms.dto.request.GmsPromptPreviewRequest;
import com.nemonicworld.gms.dto.response.GmsPromptPreviewResponse;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GmsPromptPreviewService {

    private static final String FEATURE_TYPE_FORTUNE = "fortune";
    private static final String REQUIRED_CONTENT_MESSAGE = "프롬프트 본문을 입력해야 합니다.";
    private static final String REQUIRED_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입을 입력해야 합니다.";
    private static final String UNSUPPORTED_FEATURE_TYPE_MESSAGE = "지원하지 않는 GMS 프롬프트 미리보기 타입입니다.";
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";

    private final FortuneGenerationService fortuneGenerationService;
    private final FortuneCardRenderer fortuneCardRenderer;

    public GmsPromptPreviewService(FortuneGenerationService fortuneGenerationService,
        FortuneCardRenderer fortuneCardRenderer) {
        this.fortuneGenerationService = fortuneGenerationService;
        this.fortuneCardRenderer = fortuneCardRenderer;
    }

    public GmsPromptPreviewResponse preview(GmsPromptPreviewRequest request) {
        if (request == null) {
            throw new BadRequestException(REQUIRED_CONTENT_MESSAGE);
        }

        String featureType = normalizeRequiredTrimmed(request.featureType(), REQUIRED_FEATURE_TYPE_MESSAGE)
            .toLowerCase(Locale.ROOT);
        if (!FEATURE_TYPE_FORTUNE.equals(featureType)) {
            throw new BadRequestException(UNSUPPORTED_FEATURE_TYPE_MESSAGE);
        }

        String content = normalizeRequired(request.content(), REQUIRED_CONTENT_MESSAGE);
        JsonNode saju = fortuneGenerationService.validateAndGetSaju(request.sampleSaju());
        FortuneGmsResult result = fortuneGenerationService.generatePreview(content, saju);
        String previewImageBase64 = toPngDataUrl(fortuneCardRenderer.render(result, saju));

        return new GmsPromptPreviewResponse(featureType, fortuneGenerationService.toFortuneResult(result),
            fortuneGenerationService.toSajuInfo(saju), fortuneGenerationService.toFortuneDesign(result),
            previewImageBase64);
    }

    private String toPngDataUrl(byte[] imageBytes) {
        return PNG_DATA_URL_PREFIX + Base64.getEncoder().encodeToString(imageBytes);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }

        return value;
    }

    private String normalizeRequiredTrimmed(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }
}
