package com.nemonicworld.fortune.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ServiceUnavailableException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneCreateResponse;
import com.nemonicworld.fortune.repository.FortuneCreateCommand;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.fortune.service.gms.FortuneGmsClient;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 오늘의 운세 생성 가능 여부와 생성 정책을 처리하는 서비스입니다.
 */
@Service
public class FortuneServiceImpl implements FortuneService {

    private static final Logger log = LoggerFactory.getLogger(FortuneServiceImpl.class);

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String FEATURE_TYPE_FORTUNE = "fortune";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String INVALID_SAJU_MESSAGE = "만세력 결과 정보가 올바르지 않습니다.";
    private static final String FORTUNE_ALREADY_CREATED_MESSAGE = "오늘의 운세는 이미 생성했습니다. 내일 다시 이용해주세요.";
    private static final String FORTUNE_GMS_UNAVAILABLE_MESSAGE = "운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.";
    private static final String FORTUNE_GMS_RESULT_INVALID_MESSAGE = "운세 생성 결과 형식이 올바르지 않습니다.";
    private static final String FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE = "운세 결과를 저장 형식으로 변환할 수 없습니다.";
    private static final String DEFAULT_PROMPT_TEMPLATE = """
        프론트엔드 만세력 결과를 바탕으로 오늘의 운세를 생성한다.
        응답은 title, summary, overallLuck, loveLuck, workLuck, moneyLuck, luckyColor, luckyKeyword,
        caution, postitLine, cardTheme, bgColor, accentColor, iconKey를 포함해야 한다.
        """;
    private static final int GMS_MAX_ATTEMPTS = 3;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String[] REQUIRED_SAJU_FIELDS = {"calendarType", "yearPillar", "monthPillar", "dayPillar",
        "hourPillar", "dayMasterElement", "dayBranchElement", "dayMasterYinYang", "dayBranchYinYang"};

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

        if (todayFortune.isEmpty()) {
            return new FortuneAvailabilityResponse(true, today, null, null, nextAvailableAt);
        }

        FortuneTodayRow row = todayFortune.get();
        return new FortuneAvailabilityResponse(false, row.fortuneDate(), row.fortuneId().toString(), row.createdAt(),
            nextAvailableAt);
    }

    /**
     * 만세력 결과로 오늘의 운세를 생성하고 artifact/fortune_artifact/gallery에 저장합니다.
     */
    @Transactional
    @Override
    public FortuneCreateResponse createFortune(String userUuidValue, FortuneCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        JsonNode saju = validateAndGetSaju(request);

        if (fortuneRepository.findTodayFortune(user.getId(), today).isPresent()) {
            throw new ConflictException(FORTUNE_ALREADY_CREATED_MESSAGE);
        }

        log.info("business_event event_name=fortune_request user_uuid={} fortune_date={}", user.getId(), today);

        String promptTemplate = findFortunePromptTemplate();
        FortuneGmsResult gmsResult = generateFortune(promptTemplate, saju);
        validateGmsResult(gmsResult);

        UUID fortuneId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(KST_ZONE).truncatedTo(ChronoUnit.SECONDS);
        String imageObjectKey = createFortuneImageObjectKey(today, fortuneId);
        String description = createDescription(saju, gmsResult);
        String artifactMeta = createArtifactMeta(today);
        byte[] cardImageBytes = fortuneCardRenderer.render(gmsResult, saju);

        fortuneCardStorage.upload(imageObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        saveFortune(user, today, fortuneId, galleryId, imageObjectKey, description, artifactMeta, now);

        log.info("business_event event_name=fortune_gms_success user_uuid={} fortune_id={} fortune_date={}",
            user.getId(), fortuneId, today);

        return new FortuneCreateResponse(fortuneId.toString(), today, gmsResult.title(), gmsResult.summary(),
            gmsResult.overallLuck(), gmsResult.loveLuck(), gmsResult.workLuck(), gmsResult.moneyLuck(),
            gmsResult.luckyColor(), gmsResult.luckyKeyword(), gmsResult.caution(), gmsResult.postitLine());
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

    private String findFortunePromptTemplate() {
        return gmsPromptRepository.findLatestActiveByFeatureType(FEATURE_TYPE_FORTUNE)
            .map(prompt -> prompt.getContent()).orElse(DEFAULT_PROMPT_TEMPLATE);
    }

    private FortuneGmsResult generateFortune(String promptTemplate, JsonNode saju) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= GMS_MAX_ATTEMPTS; attempt++) {
            try {
                return fortuneGmsClient.generate(promptTemplate, saju);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("business_event event_name=fortune_gms_retry attempt={}", attempt, e);
            }
        }

        throw new ServiceUnavailableException(FORTUNE_GMS_UNAVAILABLE_MESSAGE, lastFailure);
    }

    private void validateGmsResult(FortuneGmsResult result) {
        if (result == null || !StringUtils.hasText(result.title()) || !StringUtils.hasText(result.summary())
            || !StringUtils.hasText(result.luckyColor()) || !StringUtils.hasText(result.luckyKeyword())
            || !StringUtils.hasText(result.postitLine()) || !StringUtils.hasText(result.cardTheme())
            || !StringUtils.hasText(result.bgColor()) || !StringUtils.hasText(result.accentColor())
            || !StringUtils.hasText(result.iconKey()) || !isScore(result.overallLuck()) || !isScore(result.loveLuck())
            || !isScore(result.workLuck()) || !isScore(result.moneyLuck()) || !isHexColor(result.bgColor())
            || !isHexColor(result.accentColor())) {
            throw new BadRequestException(FORTUNE_GMS_RESULT_INVALID_MESSAGE);
        }
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
            throw new IllegalStateException(FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    private String createArtifactMeta(LocalDate fortuneDate) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("fortuneDate", fortuneDate.toString());

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE, e);
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
}
