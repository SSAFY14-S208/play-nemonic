package com.nemonicworld.gms.service.prompt;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.fortune.service.FortunePromptTemplateProvider;
import com.nemonicworld.fortune.service.FortunePromptTemplateProvider.CurrentFortunePrompt;
import com.nemonicworld.gms.dto.response.GmsPromptCurrentResponse;
import com.nemonicworld.gms.dto.response.GmsPromptListResponse;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GmsPromptQueryUseCase {

    private static final String PROMPT_NOT_FOUND_MESSAGE = "GMS 프롬프트를 찾을 수 없습니다.";
    private static final String REQUIRED_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입을 입력해야 합니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String INVALID_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입이 올바르지 않습니다.";
    private static final String INVALID_STATUS_MESSAGE = "프롬프트 활성 상태 값이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final GmsPromptRepository gmsPromptRepository;
    private final FortunePromptTemplateProvider fortunePromptTemplateProvider;

    public GmsPromptQueryUseCase(GmsPromptRepository gmsPromptRepository,
        FortunePromptTemplateProvider fortunePromptTemplateProvider) {
        this.gmsPromptRepository = gmsPromptRepository;
        this.fortunePromptTemplateProvider = fortunePromptTemplateProvider;
    }

    @Transactional(readOnly = true)
    public GmsPromptListResponse getPrompts(AdminPrincipal adminPrincipal, String keyword, String featureType,
        String status, String pageValue, String sizeValue) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        String normalizedFeatureType = normalizeOptionalFeatureType(featureType);
        String normalizedStatus = normalizeOptionalStatus(status);

        long totalElements = gmsPromptRepository.countActivePrompts(normalizedKeyword, normalizedFeatureType,
            normalizedStatus);
        List<GmsPromptResponse> items = gmsPromptRepository.findActivePrompts(normalizedKeyword, normalizedFeatureType,
            normalizedStatus, size, calculateOffset(page, size)).stream().map(GmsPromptResponse::from).toList();

        return new GmsPromptListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public GmsPromptResponse getPrompt(AdminPrincipal adminPrincipal, Long promptId) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        return GmsPromptResponse.from(gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE)));
    }

    @Transactional(readOnly = true)
    public GmsPromptCurrentResponse getCurrentPrompt(AdminPrincipal adminPrincipal, String featureType) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        String normalizedFeatureType = normalizeRequiredTrimmed(featureType, REQUIRED_FEATURE_TYPE_MESSAGE)
            .toLowerCase(Locale.ROOT);
        if (FortunePromptTemplateProvider.FEATURE_TYPE_FORTUNE.equals(normalizedFeatureType)) {
            CurrentFortunePrompt currentPrompt = fortunePromptTemplateProvider.resolveCurrent();
            GmsPromptResponse response = currentPrompt.prompt() == null
                ? GmsPromptResponse.defaultFortune(fortunePromptTemplateProvider.defaultPromptTemplate())
                : GmsPromptResponse.from(currentPrompt.prompt());

            return new GmsPromptCurrentResponse(normalizedFeatureType, currentPrompt.source(), response);
        }

        String validatedFeatureType = normalizeOptionalFeatureType(normalizedFeatureType);
        GmsPrompt currentPrompt = gmsPromptRepository.findCurrentByFeatureType(validatedFeatureType)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        return new GmsPromptCurrentResponse(validatedFeatureType, FortunePromptTemplateProvider.SOURCE_DATABASE,
            GmsPromptResponse.from(currentPrompt));
    }

    private String normalizeRequiredTrimmed(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }

    private String normalizeOptionalKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalFeatureType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedFeatureType = value.trim().toLowerCase(Locale.ROOT);
        if (!normalizedFeatureType.equals("fortune") && !normalizedFeatureType.equals("sticker")) {
            throw new BadRequestException(INVALID_FEATURE_TYPE_MESSAGE);
        }

        return normalizedFeatureType;
    }

    private String normalizeOptionalStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedStatus = value.trim().toLowerCase(Locale.ROOT);
        if (!normalizedStatus.equals("active") && !normalizedStatus.equals("not_active")
            && !normalizedStatus.equals("all")) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }

        return "all".equals(normalizedStatus) ? null : normalizedStatus;
    }

    private int parsePage(String pageValue) {
        int page = parseIntegerOrDefault(pageValue, DEFAULT_PAGE);
        if (page < 0) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return page;
    }

    private int parseSize(String sizeValue) {
        int size = parseIntegerOrDefault(sizeValue, DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return size;
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }
}
