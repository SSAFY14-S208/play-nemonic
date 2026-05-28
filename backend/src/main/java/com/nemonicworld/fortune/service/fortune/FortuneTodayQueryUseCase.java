package com.nemonicworld.fortune.service.fortune;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.logging.FortuneEventLogger;
import com.nemonicworld.fortune.repository.FortuneDetailRow;
import com.nemonicworld.fortune.repository.FortuneRepository;
import com.nemonicworld.fortune.service.FortuneGenerationService;
import com.nemonicworld.fortune.service.image.FortuneCardRenderer;
import com.nemonicworld.fortune.service.image.FortuneCardStorage;
import com.nemonicworld.fortune.service.support.FortuneDescriptionSupport;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FortuneTodayQueryUseCase {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FORTUNE_NOT_FOUND_MESSAGE = "오늘 생성된 운세를 찾을 수 없습니다.";

    private final FortuneRepository fortuneRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FortuneGenerationService fortuneGenerationService;
    private final FortuneCardRenderer fortuneCardRenderer;
    private final FortuneCardStorage fortuneCardStorage;
    private final FortuneDescriptionSupport fortuneDescriptionSupport;

    public FortuneTodayQueryUseCase(FortuneRepository fortuneRepository, AnonymousUserResolver anonymousUserResolver,
        FortuneGenerationService fortuneGenerationService, FortuneCardRenderer fortuneCardRenderer,
        FortuneCardStorage fortuneCardStorage, FortuneDescriptionSupport fortuneDescriptionSupport) {
        this.fortuneRepository = fortuneRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fortuneGenerationService = fortuneGenerationService;
        this.fortuneCardRenderer = fortuneCardRenderer;
        this.fortuneCardStorage = fortuneCardStorage;
        this.fortuneDescriptionSupport = fortuneDescriptionSupport;
    }

    @Transactional
    public FortuneResponse getTodayFortune(String userUuidValue) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        LocalDate today = LocalDate.now(KST_ZONE);
        FortuneDetailRow row = fortuneRepository.findTodayFortuneDetail(user.getId(), today)
            .orElseThrow(() -> new NotFoundException(FORTUNE_NOT_FOUND_MESSAGE));
        JsonNode description = fortuneDescriptionSupport.parseDescription(row.description());
        ensureCurrentFortuneImage(row, description);
        FortuneResponse response = toFortuneResponse(row, description);

        FortuneEventLogger.apiBusiness("fortune_reissued", user.getId(), FortuneEventLogger.metadata("fortune_id",
            row.fortuneId(), "fortune_date", row.fortuneDate(), "result", "success"));

        return response;
    }

    private FortuneResponse toFortuneResponse(FortuneDetailRow row, JsonNode description) {
        return new FortuneResponse(row.fortuneId().toString(), row.fortuneDate(),
            fortuneGenerationService.toFortuneResult(description), fortuneGenerationService.toSajuInfo(description),
            fortuneGenerationService.toFortuneDesign(description));
    }

    private void ensureCurrentFortuneImage(FortuneDetailRow row, JsonNode description) {
        String currentObjectKey = row.fortuneImageObjectKey();
        String expectedObjectKey = fortuneDescriptionSupport.createFortuneImageObjectKey(row.fortuneDate(),
            row.fortuneId());
        if (expectedObjectKey.equals(currentObjectKey)) {
            return;
        }

        byte[] cardImageBytes = fortuneCardRenderer.render(fortuneDescriptionSupport.toGmsResult(description),
            fortuneDescriptionSupport.renderSaju(description, row.fortuneDate()));
        fortuneCardStorage.upload(expectedObjectKey, cardImageBytes, PNG_CONTENT_TYPE);
        fortuneRepository.updateFortuneImageObjectKey(row.fortuneId(), expectedObjectKey,
            LocalDateTime.now(KST_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }
}
