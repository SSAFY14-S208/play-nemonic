package com.nemonicworld.fortune.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.ServiceUnavailableException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneDesign;
import com.nemonicworld.fortune.dto.response.FortuneResponse.FortuneResult;
import com.nemonicworld.fortune.dto.response.FortuneResponse.SajuInfo;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.repository.FortuneCreateCommand;
import com.nemonicworld.fortune.repository.FortuneDetailRow;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.fortune.service.gms.FortuneGmsClient;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 오늘의 운세 생성 가능 여부와 생성 정책을 처리하는 서비스입니다.
 */
@Service
public class FortuneServiceImpl implements FortuneService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String FEATURE_TYPE_FORTUNE = "fortune";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String INVALID_SAJU_MESSAGE = "만세력 결과 정보가 올바르지 않습니다.";
    private static final String FORTUNE_ALREADY_CREATED_MESSAGE = "오늘의 운세는 이미 생성했습니다. 내일 다시 이용해주세요.";
    private static final String FORTUNE_NOT_FOUND_MESSAGE = "오늘 생성된 운세를 찾을 수 없습니다.";
    private static final String FORTUNE_GMS_UNAVAILABLE_MESSAGE = "운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.";
    private static final String FORTUNE_GMS_RESULT_INVALID_MESSAGE = "운세 생성 결과 형식이 올바르지 않습니다.";
    private static final String FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE = "운세 결과를 저장 형식으로 변환할 수 없습니다.";
    private static final String FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE = "저장된 운세 결과 형식이 올바르지 않습니다.";
    private static final String DEFAULT_PROMPT_TEMPLATE = """
        프론트엔드 만세력 결과를 바탕으로 오늘의 운세를 생성한다.
        응답은 title, summary, overallLuck, loveLuck, workLuck, moneyLuck, luckyColor, luckyKeyword,
        caution, postitLine을 포함해야 한다.
        cardTheme, bgColor, accentColor, iconKey는 카드 에셋 메타데이터가 없으면 null로 둘 수 있다.
        """;
    private static final int GMS_MAX_ATTEMPTS = 3;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String[] REQUIRED_SAJU_FIELDS = {"calendarType", "yearPillar", "monthPillar", "dayPillar",
        "dayMasterElement", "dayBranchElement", "dayMasterYinYang", "dayBranchYinYang"};

    private final FortuneRepository fortuneRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final GmsPromptRepository gmsPromptRepository;
    private final FortuneGmsClient fortuneGmsClient;
    private final FortuneCardRenderer fortuneCardRenderer;
    private final FortuneCardStorage fortuneCardStorage;
    private final ObjectMapper objectMapper;

    public FortuneServiceImpl(FortuneRepository fortuneRepository, AnonymousUserResolver anonymousUserResolver,
        GmsPromptRepository gmsPromptRepository, FortuneGmsClient fortuneGmsClient,
        FortuneCardRenderer fortuneCardRenderer, FortuneCardStorage fortuneCardStorage, ObjectMapper objectMapper) {
        this.fortuneRepository = fortuneRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.gmsPromptRepository = gmsPromptRepository;
        this.fortuneGmsClient = fortuneGmsClient;
        this.fortuneCardRenderer = fortuneCardRenderer;
        this.fortuneCardStorage = fortuneCardStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * 기존 사용자를 확인한 뒤 KST 오늘 날짜에 운세가 이미 생성되었는지 조회합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public FortuneAvailabilityResponse getTodayAvailability(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        OffsetDateTime nextAvailableAt = today.plusDays(1).atStartOfDay(KST_ZONE).toOffsetDateTime();
        Optional<FortuneTodayRow> todayFortune = fortuneRepository.findTodayFortune(user.getId(), today);
        FortuneAvailabilityResponse response;

        if (todayFortune.isEmpty()) {
            response = new FortuneAvailabilityResponse(true, today, null, null, nextAvailableAt);
        } else {
            FortuneTodayRow row = todayFortune.get();
            response = new FortuneAvailabilityResponse(false, row.fortuneDate(), row.fortuneId().toString(),
                row.createdAt(), nextAvailableAt);
        }

        logAvailabilityChecked(user.getId(), response);

        return response;
    }

    /**
     * KST 오늘 날짜에 이미 생성된 운세 결과를 저장된 JSON에서 복원해 반환합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public FortuneResponse getTodayFortune(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        FortuneDetailRow row = fortuneRepository.findTodayFortuneDetail(user.getId(), today)
            .orElseThrow(() -> new NotFoundException(FORTUNE_NOT_FOUND_MESSAGE));
        FortuneResponse response = toFortuneResponse(row);

        FortuneEventLogger.apiBusiness("fortune_reissued", user.getId(), FortuneEventLogger.metadata("fortune_id",
            row.fortuneId(), "fortune_date", row.fortuneDate(), "result", "success"));

        return response;
    }

    /**
     * 만세력 결과로 오늘의 운세를 생성하고 artifact/fortune_artifact/gallery에 저장합니다.
     */
    @Transactional
    @Override
    public FortuneResponse createFortune(String userUuidValue, FortuneCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        JsonNode saju = validateAndGetSaju(request);
        FortuneEventLogger.apiBusiness("fortune_create_requested", user.getId(),
            FortuneEventLogger.metadata("fortune_date", today, "result", "requested"));

        Optional<FortuneTodayRow> todayFortune = fortuneRepository.findTodayFortune(user.getId(), today);
        if (todayFortune.isPresent()) {
            logDailyLimitBlocked(user.getId(), todayFortune.get());
            throw new ConflictException(FORTUNE_ALREADY_CREATED_MESSAGE);
        }

        FortunePrompt prompt = findFortunePromptTemplate();
        FortuneGmsResult gmsResult = generateFortune(prompt.template(), prompt.version(), saju, user.getId(), today);

        UUID fortuneId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(KST_ZONE).truncatedTo(ChronoUnit.SECONDS);
        String imageObjectKey = createFortuneImageObjectKey(today, fortuneId);
        String description = createDescription(saju, gmsResult);
        String artifactMeta = createArtifactMeta(today);
        byte[] cardImageBytes = fortuneCardRenderer.render(gmsResult, saju);

        fortuneCardStorage.upload(imageObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        saveFortune(user, today, fortuneId, galleryId, imageObjectKey, description, artifactMeta, now);

        FortuneEventLogger.apiBusiness("fortune_created", user.getId(),
            FortuneEventLogger.metadata("fortune_id", fortuneId, "gallery_id", galleryId, "fortune_date", today,
                "prompt_version", prompt.version(), "result", "success"));

        return new FortuneResponse(fortuneId.toString(), today, toFortuneResult(gmsResult), toSajuInfo(saju),
            toFortuneDesign(gmsResult));
    }

    private JsonNode validateAndGetSaju(FortuneCreateRequest request) {
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

    private FortunePrompt findFortunePromptTemplate() {
        return gmsPromptRepository.findLatestActiveByFeatureType(FEATURE_TYPE_FORTUNE).map(this::toFortunePrompt)
            .orElse(new FortunePrompt(DEFAULT_PROMPT_TEMPLATE, "default"));
    }

    private FortunePrompt toFortunePrompt(GmsPrompt prompt) {
        return new FortunePrompt(prompt.getContent(), String.valueOf(prompt.getId()));
    }

    private FortuneGmsResult generateFortune(String promptTemplate, String promptVersion, JsonNode saju, UUID userUuid,
        LocalDate fortuneDate) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= GMS_MAX_ATTEMPTS; attempt++) {
            long startedAtNanos = System.nanoTime();
            try {
                FortuneGmsResult result = fortuneGmsClient.generate(promptTemplate, saju);
                validateGmsResult(result);
                long latencyMs = elapsedMillis(startedAtNanos);
                FortuneEventLogger.apiBusiness("fortune_gms_succeeded", userUuid,
                    FortuneEventLogger.metadata("fortune_date", fortuneDate, "attempt_count", attempt, "retry_count",
                        attempt - 1, "gms_latency_ms", latencyMs, "prompt_version", promptVersion, "result",
                        "success"));
                return result;
            } catch (RuntimeException e) {
                long latencyMs = elapsedMillis(startedAtNanos);
                lastFailure = e;
                if (attempt < GMS_MAX_ATTEMPTS) {
                    FortuneEventLogger.apiBusinessWarn("fortune_gms_retried", "fortune gms request will retry",
                        userUuid,
                        FortuneEventLogger.metadata("fortune_date", fortuneDate, "attempt", attempt, "retry_count",
                            attempt, "gms_latency_ms", latencyMs, "prompt_version", promptVersion, "result", "retry"),
                        e);
                } else {
                    FortuneEventLogger.apiBusinessWarn("fortune_gms_failed", "fortune gms request failed", userUuid,
                        FortuneEventLogger.metadata("fortune_date", fortuneDate, "attempt_count", attempt,
                            "retry_count", attempt - 1, "gms_latency_ms", latencyMs, "prompt_version", promptVersion,
                            "result", "failed"),
                        e);
                }
            }
        }

        throw new ServiceUnavailableException(FORTUNE_GMS_UNAVAILABLE_MESSAGE, lastFailure);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private void logAvailabilityChecked(UUID userUuid, FortuneAvailabilityResponse response) {
        FortuneEventLogger.apiBusiness("fortune_availability_checked", userUuid,
            FortuneEventLogger.metadata("fortune_date", response.fortuneDate(), "available", response.available(),
                "today_fortune_id", response.todayFortuneId(), "result", "success"));
    }

    private void logDailyLimitBlocked(UUID userUuid, FortuneTodayRow row) {
        FortuneEventLogger.apiBusiness("fortune_daily_limit_blocked", userUuid, FortuneEventLogger
            .metadata("fortune_date", row.fortuneDate(), "today_fortune_id", row.fortuneId(), "result", "blocked"));
    }

    private FortuneResponse toFortuneResponse(FortuneDetailRow row) {
        try {
            JsonNode description = objectMapper.readTree(row.description());
            return new FortuneResponse(row.fortuneId().toString(), row.fortuneDate(), toFortuneResult(description),
                toSajuInfo(description), toFortuneDesign(description));
        } catch (JsonProcessingException e) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }
    }

    private FortuneResult toFortuneResult(FortuneGmsResult result) {
        return new FortuneResult(result.title(), result.summary(), result.overallLuck(), result.loveLuck(),
            result.workLuck(), result.moneyLuck(), result.luckyColor(), result.luckyKeyword(), result.caution(),
            result.postitLine());
    }

    private FortuneResult toFortuneResult(JsonNode description) {
        return new FortuneResult(requiredText(description, "title"), requiredText(description, "summary"),
            requiredScore(description, "overallLuck"), requiredScore(description, "loveLuck"),
            requiredScore(description, "workLuck"), requiredScore(description, "moneyLuck"),
            requiredText(description, "luckyColor"), requiredText(description, "luckyKeyword"),
            nullableText(description, "caution"), requiredText(description, "postitLine"));
    }

    private SajuInfo toSajuInfo(JsonNode description) {
        JsonNode saju = description.path("saju");
        JsonNode source = saju.isObject() ? saju : description;
        return new SajuInfo(nullableText(source, "calendarType"), nullableText(source, "yearPillar"),
            nullableText(source, "monthPillar"), nullableText(source, "dayPillar"), nullableText(source, "hourPillar"),
            nullableText(source, "dayMasterElement"), nullableText(source, "dayBranchElement"),
            nullableText(source, "dayMasterYinYang"), nullableText(source, "dayBranchYinYang"));
    }

    private FortuneDesign toFortuneDesign(FortuneGmsResult result) {
        return new FortuneDesign(result.cardTheme(), result.bgColor(), result.accentColor(), result.iconKey());
    }

    private FortuneDesign toFortuneDesign(JsonNode description) {
        return new FortuneDesign(nullableText(description, "cardTheme"), nullableText(description, "bgColor"),
            nullableText(description, "accentColor"), nullableText(description, "iconKey"));
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

    private int requiredScore(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || !value.canConvertToInt() || !isScore(value.asInt())) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }

        return value.asInt();
    }

    private void validateGmsResult(FortuneGmsResult result) {
        if (result == null || !StringUtils.hasText(result.title()) || !StringUtils.hasText(result.summary())
            || !StringUtils.hasText(result.luckyColor()) || !StringUtils.hasText(result.luckyKeyword())
            || !StringUtils.hasText(result.postitLine()) || !isScore(result.overallLuck())
            || !isScore(result.loveLuck()) || !isScore(result.workLuck()) || !isScore(result.moneyLuck())
            || hasInvalidHexColor(result.bgColor()) || hasInvalidHexColor(result.accentColor())) {
            throw new ServiceUnavailableException(FORTUNE_GMS_RESULT_INVALID_MESSAGE);
        }
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

    private String createFortuneImageObjectKey(LocalDate fortuneDate, UUID fortuneId) {
        return "fortune/cards/%04d/%02d/%02d/%s/card.png".formatted(fortuneDate.getYear(), fortuneDate.getMonthValue(),
            fortuneDate.getDayOfMonth(), fortuneId);
    }

    private String createDescription(JsonNode saju, FortuneGmsResult result) {
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
        description.put("luckyKeyword", result.luckyKeyword());
        if (result.caution() == null) {
            description.putNull("caution");
        } else {
            description.put("caution", result.caution());
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

    private String createArtifactMeta(LocalDate fortuneDate) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("fortuneDate", fortuneDate.toString());

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    private void saveFortune(AppUser user, LocalDate today, UUID fortuneId, UUID galleryId, String imageObjectKey,
        String description, String artifactMeta, LocalDateTime now) {
        FortuneCreateCommand command = new FortuneCreateCommand(fortuneId, galleryId, user.getId(), today, description,
            imageObjectKey, artifactMeta, now);
        try {
            fortuneRepository.saveFortune(command);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(FORTUNE_ALREADY_CREATED_MESSAGE);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText().trim();
    }

    private record FortunePrompt(String template, String version) {
    }
}
