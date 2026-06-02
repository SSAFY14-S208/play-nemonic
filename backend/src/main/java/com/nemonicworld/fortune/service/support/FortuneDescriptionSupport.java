package com.nemonicworld.fortune.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.service.FortuneGenerationService;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FortuneDescriptionSupport {

    private static final String FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE = "운세 결과를 저장 형식으로 변환할 수 없습니다.";
    private static final String FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE = "저장된 운세 결과 형식이 올바르지 않습니다.";
    private static final String FORTUNE_CARD_FILE_NAME = "card-template-v1.png";

    private final ObjectMapper objectMapper;
    private final FortuneGenerationService fortuneGenerationService;

    public FortuneDescriptionSupport(ObjectMapper objectMapper, FortuneGenerationService fortuneGenerationService) {
        this.objectMapper = objectMapper;
        this.fortuneGenerationService = fortuneGenerationService;
    }

    public JsonNode parseDescription(String description) {
        try {
            return objectMapper.readTree(description);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }
    }

    public String createArtifactMeta(LocalDate fortuneDate) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("fortuneDate", fortuneDate.toString());

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    public JsonNode renderSaju(JsonNode sajuSource, LocalDate fortuneDate) {
        ObjectNode renderSaju = objectMapper.createObjectNode();
        JsonNode nestedSaju = sajuSource.path("saju");
        JsonNode source = nestedSaju.isObject() ? nestedSaju : sajuSource;
        renderSaju.put("calendarType", nullableText(source, "calendarType"));
        renderSaju.put("yearPillar", nullableText(source, "yearPillar"));
        renderSaju.put("monthPillar", nullableText(source, "monthPillar"));
        renderSaju.put("dayPillar", nullableText(source, "dayPillar"));
        renderSaju.put("hourPillar", nullableText(source, "hourPillar"));
        renderSaju.put("dayMasterElement", nullableText(source, "dayMasterElement"));
        renderSaju.put("dayBranchElement", nullableText(source, "dayBranchElement"));
        renderSaju.put("dayMasterYinYang", nullableText(source, "dayMasterYinYang"));
        renderSaju.put("dayBranchYinYang", nullableText(source, "dayBranchYinYang"));
        renderSaju.put("fortuneDate", fortuneDate.toString());

        return renderSaju;
    }

    public FortuneGmsResult toGmsResult(JsonNode description) {
        FortuneResponse.FortuneResult fortune = fortuneGenerationService.toFortuneResult(description);
        FortuneResponse.FortuneDesign design = fortuneGenerationService.toFortuneDesign(description);

        return new FortuneGmsResult(fortune.title(), fortune.summary(), fortune.overallLuck(), fortune.loveLuck(),
            fortune.workLuck(), fortune.moneyLuck(), fortune.luckyColor(), fortune.luckyKeyword(),
            fortune.luckyDirection(), fortune.caution(), fortune.postitLine(), design.cardTheme(), design.bgColor(),
            design.accentColor(), design.iconKey());
    }

    public String createFortuneImageObjectKey(LocalDate fortuneDate, UUID fortuneId) {
        return "fortune/cards/%04d/%02d/%02d/%s/%s".formatted(fortuneDate.getYear(), fortuneDate.getMonthValue(),
            fortuneDate.getDayOfMonth(), fortuneId, FORTUNE_CARD_FILE_NAME);
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText(null);
    }
}
