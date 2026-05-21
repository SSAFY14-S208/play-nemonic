package com.nemonicworld.fortune.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.repository.FortuneCreateCommand;
import com.nemonicworld.fortune.repository.FortuneDetailRow;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.fortune.service.FortunePromptTemplateProvider.CurrentFortunePrompt;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 운세 생성 가능 여부와 생성 정책을 처리하는 서비스입니다.
 */
@Service
public class FortuneServiceImpl implements FortuneService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FORTUNE_ALREADY_CREATED_MESSAGE = "오늘의 운세는 이미 생성했습니다. 내일 다시 이용해주세요.";
    private static final String FORTUNE_NOT_FOUND_MESSAGE = "오늘 생성된 운세를 찾을 수 없습니다.";
    private static final String FORTUNE_DESCRIPTION_SERIALIZATION_ERROR_MESSAGE = "운세 결과를 저장 형식으로 변환할 수 없습니다.";
    private static final String FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE = "저장된 운세 결과 형식이 올바르지 않습니다.";
    private static final String FORTUNE_CARD_FILE_NAME = "card-template-v1.png";
    private final FortuneRepository fortuneRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FortunePromptTemplateProvider fortunePromptTemplateProvider;
    private final FortuneGenerationService fortuneGenerationService;
    private final FortuneCardRenderer fortuneCardRenderer;
    private final FortuneCardStorage fortuneCardStorage;
    private final ObjectMapper objectMapper;

    public FortuneServiceImpl(FortuneRepository fortuneRepository, AnonymousUserResolver anonymousUserResolver,
        FortunePromptTemplateProvider fortunePromptTemplateProvider, FortuneGenerationService fortuneGenerationService,
        FortuneCardRenderer fortuneCardRenderer, FortuneCardStorage fortuneCardStorage, ObjectMapper objectMapper) {
        this.fortuneRepository = fortuneRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fortunePromptTemplateProvider = fortunePromptTemplateProvider;
        this.fortuneGenerationService = fortuneGenerationService;
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
    @Transactional
    @Override
    public FortuneResponse getTodayFortune(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        FortuneDetailRow row = fortuneRepository.findTodayFortuneDetail(user.getId(), today)
            .orElseThrow(() -> new NotFoundException(FORTUNE_NOT_FOUND_MESSAGE));
        JsonNode description = parseDescription(row.description());
        ensureCurrentFortuneImage(row, description);
        FortuneResponse response = toFortuneResponse(row, description);

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
        JsonNode saju = fortuneGenerationService.validateAndGetSaju(request);
        FortuneEventLogger.apiBusiness("fortune_create_requested", user.getId(),
            FortuneEventLogger.metadata("fortune_date", today, "result", "requested"));

        Optional<FortuneTodayRow> todayFortune = fortuneRepository.findTodayFortune(user.getId(), today);
        if (todayFortune.isPresent()) {
            logDailyLimitBlocked(user.getId(), todayFortune.get());
            throw new ConflictException(FORTUNE_ALREADY_CREATED_MESSAGE);
        }

        CurrentFortunePrompt prompt = fortunePromptTemplateProvider.resolveCurrent();
        FortuneGmsResult gmsResult = fortuneGenerationService.generateFortune(prompt.template(), prompt.version(), saju,
            user.getId(), today);

        UUID fortuneId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(KST_ZONE).truncatedTo(ChronoUnit.SECONDS);
        String imageObjectKey = createFortuneImageObjectKey(today, fortuneId);
        String description = fortuneGenerationService.createDescription(saju, gmsResult);
        String artifactMeta = createArtifactMeta(today);
        byte[] cardImageBytes = fortuneCardRenderer.render(gmsResult, renderSaju(saju, today));

        fortuneCardStorage.upload(imageObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        saveFortune(user, today, fortuneId, galleryId, imageObjectKey, description, artifactMeta, now);

        FortuneEventLogger.apiBusiness("fortune_created", user.getId(),
            FortuneEventLogger.metadata("fortune_id", fortuneId, "gallery_id", galleryId, "fortune_date", today,
                "prompt_version", prompt.version(), "result", "success"));

        return new FortuneResponse(fortuneId.toString(), today, fortuneGenerationService.toFortuneResult(gmsResult),
            fortuneGenerationService.toSajuInfo(saju), fortuneGenerationService.toFortuneDesign(gmsResult));
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

    private FortuneResponse toFortuneResponse(FortuneDetailRow row, JsonNode description) {
        return new FortuneResponse(row.fortuneId().toString(), row.fortuneDate(),
            fortuneGenerationService.toFortuneResult(description), fortuneGenerationService.toSajuInfo(description),
            fortuneGenerationService.toFortuneDesign(description));
    }

    private JsonNode parseDescription(String description) {
        try {
            return objectMapper.readTree(description);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(FORTUNE_DESCRIPTION_PARSE_ERROR_MESSAGE);
        }
    }

    private String createFortuneImageObjectKey(LocalDate fortuneDate, UUID fortuneId) {
        return "fortune/cards/%04d/%02d/%02d/%s/%s".formatted(fortuneDate.getYear(), fortuneDate.getMonthValue(),
            fortuneDate.getDayOfMonth(), fortuneId, FORTUNE_CARD_FILE_NAME);
    }

    private void ensureCurrentFortuneImage(FortuneDetailRow row, JsonNode description) {
        String currentObjectKey = row.fortuneImageObjectKey();
        String expectedObjectKey = createFortuneImageObjectKey(row.fortuneDate(), row.fortuneId());
        if (expectedObjectKey.equals(currentObjectKey)) {
            return;
        }

        byte[] cardImageBytes = fortuneCardRenderer.render(toGmsResult(description),
            renderSaju(description, row.fortuneDate()));
        fortuneCardStorage.upload(expectedObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        fortuneRepository.updateFortuneImageObjectKey(row.fortuneId(), expectedObjectKey,
            LocalDateTime.now(KST_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    private FortuneGmsResult toGmsResult(JsonNode description) {
        FortuneResponse.FortuneResult fortune = fortuneGenerationService.toFortuneResult(description);
        FortuneResponse.FortuneDesign design = fortuneGenerationService.toFortuneDesign(description);

        return new FortuneGmsResult(fortune.title(), fortune.summary(), fortune.overallLuck(), fortune.loveLuck(),
            fortune.workLuck(), fortune.moneyLuck(), fortune.luckyColor(), fortune.luckyKeyword(),
            fortune.luckyDirection(), fortune.caution(), fortune.postitLine(), design.cardTheme(), design.bgColor(),
            design.accentColor(), design.iconKey());
    }

    private JsonNode renderSaju(JsonNode sajuSource, LocalDate fortuneDate) {
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

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText(null);
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

}
