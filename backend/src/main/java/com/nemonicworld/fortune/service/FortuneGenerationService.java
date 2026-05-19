package com.nemonicworld.fortune.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.common.exception.ServiceUnavailableException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneDesign;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneResult;
import com.nemonicworld.fortune.dto.response.FortuneResponse.SajuInfo;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.service.gms.FortuneGmsClient;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FortuneGenerationService {

    private static final String INVALID_SAJU_MESSAGE = "만세력 결과 정보가 올바르지 않습니다.";
    private static final String FORTUNE_GMS_UNAVAILABLE_MESSAGE = "운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.";
    private static final String FORTUNE_GMS_RESULT_INVALID_MESSAGE = "운세 생성 결과 형식이 올바르지 않습니다.";
    private static final String FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE = "운세 결과를 저장 형식으로 변환할 수 없습니다.";
    private static final String FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE = "저장된 운세 결과 형식이 올바르지 않습니다.";
    private static final String DEFAULT_LUCKY_DIRECTION = "동쪽";
    private static final int CAUTION_MAX_LENGTH = 42;
    private static final int GMS_MAX_ATTEMPTS = 3;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String[] REQUIRED_SAJU_FIELDS = {"calendarType", "yearPillar", "monthPillar", "dayPillar",
        "dayMasterElement", "dayBranchElement", "dayMasterYinYang", "dayBranchYinYang"};

    private final FortuneGmsClient fortuneGmsClient;
    private final ObjectMapper objectMapper;

    public FortuneGenerationService(FortuneGmsClient fortuneGmsClient, ObjectMapper objectMapper) {
        this.fortuneGmsClient = fortuneGmsClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode validateAndGetSaju(FortuneCreateRequest request) {
        if (request == null) {
            throw new BadRequestException(INVALID_SAJU_MESSAGE);
        }

        JsonNode saju = objectMapper.valueToTree(request);
        for (String fieldName : REQUIRED_SAJU_FIELDS) {
            if (!StringUtils.hasText(text(saju, fieldName))) {
                throw new BadRequestException(INVALID_SAJU_MESSAGE);
            }
        }

        return saju;
    }

    public FortuneGmsResult generateFortune(String promptTemplate, String promptVersion, JsonNode saju, UUID userUuid,
        LocalDate fortuneDate) {
        return generateWithRetries(promptTemplate, promptVersion, saju,
            new FortuneGenerationLogContext(userUuid, fortuneDate));
    }

    public FortuneGmsResult generatePreview(String promptTemplate, JsonNode saju) {
        return generateWithRetries(promptTemplate, "preview", saju, null);
    }

    public FortuneResult toFortuneResult(FortuneGmsResult result) {
        return new FortuneResult(result.title(), result.summary(), result.overallLuck(), result.loveLuck(),
            result.workLuck(), result.moneyLuck(), result.luckyColor(),
            FortuneLuckyColorResolver.resolveHex(result.luckyColor(), result.title()), result.luckyKeyword(),
            result.luckyDirection(), normalizeCaution(result.caution()), result.postitLine());
    }

    public FortuneResult toFortuneResult(JsonNode description) {
        String title = requiredText(description, "title");
        String luckyColor = requiredText(description, "luckyColor");
        return new FortuneResult(title, requiredText(description, "summary"), requiredScore(description, "overallLuck"),
            requiredScore(description, "loveLuck"), requiredScore(description, "workLuck"),
            requiredScore(description, "moneyLuck"), luckyColor,
            textOrDefault(description, "luckyColorHex", FortuneLuckyColorResolver.resolveHex(luckyColor, title)),
            requiredText(description, "luckyKeyword"),
            textOrDefault(description, "luckyDirection", DEFAULT_LUCKY_DIRECTION),
            normalizeCaution(nullableText(description, "caution")), requiredText(description, "postitLine"));
    }

    public SajuInfo toSajuInfo(JsonNode description) {
        JsonNode saju = description.path("saju");
        JsonNode source = saju.isObject() ? saju : description;
        return new SajuInfo(nullableText(source, "calendarType"), nullableText(source, "yearPillar"),
            nullableText(source, "monthPillar"), nullableText(source, "dayPillar"), nullableText(source, "hourPillar"),
            nullableText(source, "dayMasterElement"), nullableText(source, "dayBranchElement"),
            nullableText(source, "dayMasterYinYang"), nullableText(source, "dayBranchYinYang"));
    }

    public FortuneDesign toFortuneDesign(FortuneGmsResult result) {
        return new FortuneDesign(result.cardTheme(), result.bgColor(), result.accentColor(), result.iconKey());
    }

    public FortuneDesign toFortuneDesign(JsonNode description) {
        return new FortuneDesign(nullableText(description, "cardTheme"), nullableText(description, "bgColor"),
            nullableText(description, "accentColor"), nullableText(description, "iconKey"));
    }

    public String createDescription(JsonNode saju, FortuneGmsResult result) {
        ObjectNode description = objectMapper.createObjectNode();
        description.put("calendarType", text(saju, "calendarType"));
        description.put("yearPillar", text(saju, "yearPillar"));
        description.put("monthPillar", text(saju, "monthPillar"));
        description.put("dayPillar", text(saju, "dayPillar"));
        description.put("hourPillar", text(saju, "hourPillar"));
        description.put("dayMasterElement", text(saju, "dayMasterElement"));
        description.put("dayBranchElement", text(saju, "dayBranchElement"));
        description.put("dayMasterYinYang", text(saju, "dayMasterYinYang"));
        description.put("dayBranchYinYang", text(saju, "dayBranchYinYang"));
        description.set("saju", saju.deepCopy());
        description.put("title", result.title());
        description.put("summary", result.summary());
        description.put("overallLuck", result.overallLuck());
        description.put("loveLuck", result.loveLuck());
        description.put("workLuck", result.workLuck());
        description.put("moneyLuck", result.moneyLuck());
        description.put("luckyColor", result.luckyColor());
        description.put("luckyColorHex", FortuneLuckyColorResolver.resolveHex(result.luckyColor(), result.title()));
        description.put("luckyKeyword", result.luckyKeyword());
        description.put("luckyDirection", result.luckyDirection());
        String caution = normalizeCaution(result.caution());
        if (caution == null) {
            description.putNull("caution");
        } else {
            description.put("caution", caution);
        }
        description.put("postitLine", result.postitLine());
        description.put("cardTheme", result.cardTheme());
        description.put("bgColor", result.bgColor());
        description.put("accentColor", result.accentColor());
        description.put("iconKey", result.iconKey());

        try {
            return objectMapper.writeValueAsString(description);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    private FortuneGmsResult generateWithRetries(String promptTemplate, String promptVersion, JsonNode saju,
        FortuneGenerationLogContext logContext) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= GMS_MAX_ATTEMPTS; attempt++) {
            long startedAtNanos = System.nanoTime();
            try {
                FortuneGmsResult result = normalizeGmsResult(fortuneGmsClient.generate(promptTemplate, saju));
                validateGmsResult(result);
                logSuccess(logContext, promptVersion, attempt, startedAtNanos);
                return result;
            } catch (RuntimeException e) {
                lastFailure = e;
                logFailure(logContext, promptVersion, attempt, startedAtNanos, e);
            }
        }

        throw new ServiceUnavailableException(FORTUNE_GMS_UNAVAILABLE_MESSAGE, lastFailure);
    }

    private void logSuccess(FortuneGenerationLogContext logContext, String promptVersion, int attempt,
        long startedAtNanos) {
        if (logContext == null) {
            return;
        }

        FortuneEventLogger.apiBusiness("fortune_gms_succeeded", logContext.userUuid(),
            FortuneEventLogger.metadata("fortune_date", logContext.fortuneDate(), "attempt_count", attempt,
                "retry_count", attempt - 1, "gms_latency_ms", elapsedMillis(startedAtNanos), "prompt_version",
                promptVersion, "result", "success"));
    }

    private void logFailure(FortuneGenerationLogContext logContext, String promptVersion, int attempt,
        long startedAtNanos, RuntimeException e) {
        if (logContext == null) {
            return;
        }

        if (attempt < GMS_MAX_ATTEMPTS) {
            FortuneEventLogger.apiBusinessWarn("fortune_gms_retried", "fortune gms request will retry",
                logContext.userUuid(),
                FortuneEventLogger.metadata("fortune_date", logContext.fortuneDate(), "attempt", attempt, "retry_count",
                    attempt, "gms_latency_ms", elapsedMillis(startedAtNanos), "prompt_version", promptVersion, "result",
                    "retry"),
                e);
        } else {
            FortuneEventLogger.apiBusinessWarn("fortune_gms_failed", "fortune gms request failed",
                logContext.userUuid(),
                FortuneEventLogger.metadata("fortune_date", logContext.fortuneDate(), "attempt_count", attempt,
                    "retry_count", attempt - 1, "gms_latency_ms", elapsedMillis(startedAtNanos), "prompt_version",
                    promptVersion, "result", "failed"),
                e);
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private void validateGmsResult(FortuneGmsResult result) {
        if (result == null || !StringUtils.hasText(result.title()) || !StringUtils.hasText(result.summary())
            || !StringUtils.hasText(result.luckyColor()) || !StringUtils.hasText(result.luckyKeyword())
            || !StringUtils.hasText(result.luckyDirection()) || !StringUtils.hasText(result.postitLine())
            || !isScore(result.overallLuck()) || !isScore(result.loveLuck()) || !isScore(result.workLuck())
            || !isScore(result.moneyLuck()) || hasInvalidHexColor(result.bgColor())
            || hasInvalidHexColor(result.accentColor())) {
            throw new ServiceUnavailableException(FORTUNE_GMS_RESULT_INVALID_MESSAGE);
        }
    }

    private FortuneGmsResult normalizeGmsResult(FortuneGmsResult result) {
        if (result == null) {
            return null;
        }

        return new FortuneGmsResult(result.title(), result.summary(), result.overallLuck(), result.loveLuck(),
            result.workLuck(), result.moneyLuck(), result.luckyColor(), result.luckyKeyword(), result.luckyDirection(),
            normalizeCaution(result.caution()), result.postitLine(), result.cardTheme(), result.bgColor(),
            result.accentColor(), result.iconKey());
    }

    private String normalizeCaution(String caution) {
        if (!StringUtils.hasText(caution)) {
            return null;
        }

        String normalized = caution.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= CAUTION_MAX_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, CAUTION_MAX_LENGTH - 3).stripTrailing() + "...";
    }

    private boolean hasInvalidHexColor(String color) {
        return StringUtils.hasText(color) && !isHexColor(color);
    }

    private boolean isScore(int score) {
        return score >= 0 && score <= 100;
    }

    private boolean isHexColor(String color) {
        return StringUtils.hasText(color) && HEX_COLOR_PATTERN.matcher(color).matches();
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }
        return value;
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText().trim();
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = nullableText(node, fieldName);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private int requiredScore(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || !value.canConvertToInt() || !isScore(value.asInt())) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }

        return value.asInt();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText().trim();
    }

    private record FortuneGenerationLogContext(UUID userUuid, LocalDate fortuneDate) {
    }
}
