package com.nemonicworld.fortune.service.fortune;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.repository.FortuneCreateCommand;
import com.nemonicworld.fortune.repository.FortuneTodayRow;
import com.nemonicworld.fortune.service.FortuneGenerationService;
import com.nemonicworld.fortune.service.FortunePromptTemplateProvider;
import com.nemonicworld.fortune.service.FortunePromptTemplateProvider.CurrentFortunePrompt;
import com.nemonicworld.fortune.service.gms.FortuneGmsResult;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
import com.nemonicworld.fortune.service.support.FortuneDescriptionSupport;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class FortuneCreateUseCase {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FORTUNE_ALREADY_CREATED_MESSAGE = "오늘의 운세는 이미 생성했습니다. 내일 다시 이용해주세요.";

    private final FortuneTransactionSupport transactionSupport;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FortunePromptTemplateProvider fortunePromptTemplateProvider;
    private final FortuneGenerationService fortuneGenerationService;
    private final FortuneCardRenderer fortuneCardRenderer;
    private final FortuneCardStorage fortuneCardStorage;
    private final FortuneDescriptionSupport fortuneDescriptionSupport;

    public FortuneCreateUseCase(FortuneTransactionSupport transactionSupport,
        AnonymousUserResolver anonymousUserResolver, FortunePromptTemplateProvider fortunePromptTemplateProvider,
        FortuneGenerationService fortuneGenerationService, FortuneCardRenderer fortuneCardRenderer,
        FortuneCardStorage fortuneCardStorage, FortuneDescriptionSupport fortuneDescriptionSupport) {
        this.transactionSupport = transactionSupport;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fortunePromptTemplateProvider = fortunePromptTemplateProvider;
        this.fortuneGenerationService = fortuneGenerationService;
        this.fortuneCardRenderer = fortuneCardRenderer;
        this.fortuneCardStorage = fortuneCardStorage;
        this.fortuneDescriptionSupport = fortuneDescriptionSupport;
    }

    public FortuneResponse createFortune(String userUuidValue, FortuneCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        JsonNode saju = fortuneGenerationService.validateAndGetSaju(request);
        FortuneEventLogger.apiBusiness("fortune_create_requested", user.getId(),
            FortuneEventLogger.metadata("fortune_date", today, "result", "requested"));

        Optional<FortuneTodayRow> todayFortune = transactionSupport.findTodayFortune(user.getId(), today);
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
        String imageObjectKey = fortuneDescriptionSupport.createFortuneImageObjectKey(today, fortuneId);
        String description = fortuneGenerationService.createDescription(saju, gmsResult);
        String artifactMeta = fortuneDescriptionSupport.createArtifactMeta(today);
        byte[] cardImageBytes = fortuneCardRenderer.render(gmsResult,
            fortuneDescriptionSupport.renderSaju(saju, today));

        fortuneCardStorage.upload(imageObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        saveFortune(user, today, fortuneId, galleryId, imageObjectKey, description, artifactMeta, now);

        FortuneEventLogger.apiBusiness("fortune_created", user.getId(),
            FortuneEventLogger.metadata("fortune_id", fortuneId, "gallery_id", galleryId, "fortune_date", today,
                "prompt_version", prompt.version(), "result", "success"));

        return new FortuneResponse(fortuneId.toString(), today, fortuneGenerationService.toFortuneResult(gmsResult),
            fortuneGenerationService.toSajuInfo(saju), fortuneGenerationService.toFortuneDesign(gmsResult));
    }

    private void logDailyLimitBlocked(UUID userUuid, FortuneTodayRow row) {
        FortuneEventLogger.apiBusiness("fortune_daily_limit_blocked", userUuid, FortuneEventLogger
            .metadata("fortune_date", row.fortuneDate(), "today_fortune_id", row.fortuneId(), "result", "blocked"));
    }

    private void saveFortune(AppUser user, LocalDate today, UUID fortuneId, UUID galleryId, String imageObjectKey,
        String description, String artifactMeta, LocalDateTime now) {
        FortuneCreateCommand command = new FortuneCreateCommand(fortuneId, galleryId, user.getId(), today, description,
            imageObjectKey, artifactMeta, now);
        try {
            transactionSupport.saveFortune(command);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(FORTUNE_ALREADY_CREATED_MESSAGE);
        }
    }
}
