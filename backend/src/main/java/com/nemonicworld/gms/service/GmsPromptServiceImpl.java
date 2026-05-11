package com.nemonicworld.gms.service;

import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptListResponse;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptInsertCommand;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import com.nemonicworld.gms.repository.GmsPromptUpdateCommand;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class GmsPromptServiceImpl implements GmsPromptService {

    private static final String PROMPT_NOT_FOUND_MESSAGE = "GMS 프롬프트를 찾을 수 없습니다.";

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String DUPLICATE_NAME_MESSAGE = "이미 등록된 GMS 프롬프트 이름입니다.";
    private static final String REQUIRED_NAME_MESSAGE = "프롬프트 이름을 입력해야 합니다.";
    private static final String REQUIRED_CONTENT_MESSAGE = "프롬프트 본문을 입력해야 합니다.";
    private static final String REQUIRED_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입을 입력해야 합니다.";
    private static final String REQUIRED_UPDATE_FIELD_MESSAGE = "수정할 프롬프트 정보를 하나 이상 입력해야 합니다.";

    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final String INVALID_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final GmsPromptRepository gmsPromptRepository;
    private final AdminAuditLogger adminAuditLogger;

    public GmsPromptServiceImpl(GmsPromptRepository gmsPromptRepository, AdminAuditLogger adminAuditLogger) {
        this.gmsPromptRepository = gmsPromptRepository;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    @Transactional
    public GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

        String name = normalizeRequiredTrimmed(request.name(), REQUIRED_NAME_MESSAGE);
        if (gmsPromptRepository.existsByName(name)) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        GmsPromptInsertCommand command = new GmsPromptInsertCommand(name,
            normalizeRequired(request.content(), REQUIRED_CONTENT_MESSAGE),
            normalizeRequiredTrimmed(request.featureType(), REQUIRED_FEATURE_TYPE_MESSAGE).toLowerCase(Locale.ROOT),
            adminPrincipal.id(), now, now);

        try {
            GmsPrompt createdPrompt = gmsPromptRepository.insertPrompt(command);
            emitAfterCommit(() -> adminAuditLogger.logPromptUpdate(adminPrincipal, createdPrompt.getId().toString(),
                "create", clientInfo, null, promptSnapshot(createdPrompt)));

            return GmsPromptResponse.from(createdPrompt);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GmsPromptListResponse getPrompts(AdminPrincipal adminPrincipal, String keyword, String featureType,
        String pageValue, String sizeValue) {
        requireAdmin(adminPrincipal);

        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        String normalizedFeatureType = normalizeOptionalFeatureType(featureType);

        long totalElements = gmsPromptRepository.countActivePrompts(normalizedKeyword, normalizedFeatureType);
        List<GmsPromptResponse> items = gmsPromptRepository
            .findActivePrompts(normalizedKeyword, normalizedFeatureType, size, calculateOffset(page, size)).stream()
            .map(GmsPromptResponse::from).toList();

        return new GmsPromptListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    @Override
    @Transactional(readOnly = true)
    public GmsPromptResponse getPrompt(AdminPrincipal adminPrincipal, Long promptId) {
        requireAdmin(adminPrincipal);

        return GmsPromptResponse.from(gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE)));
    }

    @Override
    @Transactional
    public void deletePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);

        GmsPrompt existingPrompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        LocalDateTime deletedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int deletedCount = gmsPromptRepository.softDeleteById(promptId, deletedAt);
        if (deletedCount == 0) {
            throw new NotFoundException(PROMPT_NOT_FOUND_MESSAGE);
        }

        emitAfterCommit(() -> adminAuditLogger.logPromptUpdate(adminPrincipal, promptId.toString(), "delete",
            clientInfo, promptSnapshot(existingPrompt), promptDeletedSnapshot()));
    }

    @Override
    @Transactional
    public GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptUpdateRequest request,
        AdminClientInfo clientInfo) {
        requireAdmin(adminPrincipal);
        if (request == null || request.name() == null && request.content() == null && request.featureType() == null) {
            throw new BadRequestException(REQUIRED_UPDATE_FIELD_MESSAGE);
        }

        GmsPrompt existingPrompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        String name = existingPrompt.getName();
        if (request.name() != null) {
            name = normalizeRequiredTrimmed(request.name(), REQUIRED_NAME_MESSAGE);
            if (!existingPrompt.getName().equals(name) && gmsPromptRepository.existsByName(name)) {
                throw new ConflictException(DUPLICATE_NAME_MESSAGE);
            }
        }

        String content = existingPrompt.getContent();
        if (request.content() != null) {
            content = normalizeRequired(request.content(), REQUIRED_CONTENT_MESSAGE);
        }

        String featureType = existingPrompt.getFeatureType();
        if (request.featureType() != null) {
            featureType = normalizeRequiredTrimmed(request.featureType(), REQUIRED_FEATURE_TYPE_MESSAGE)
                .toLowerCase(Locale.ROOT);
        }

        LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        GmsPromptUpdateCommand command = new GmsPromptUpdateCommand(promptId, name, content, featureType, updatedAt);

        try {
            int updatedCount = gmsPromptRepository.updatePrompt(command);
            if (updatedCount == 0) {
                throw new NotFoundException(PROMPT_NOT_FOUND_MESSAGE);
            }

            GmsPrompt updatedPrompt = gmsPromptRepository.findActiveById(promptId).orElseThrow();
            emitAfterCommit(() -> adminAuditLogger.logPromptUpdate(adminPrincipal, promptId.toString(), "update",
                clientInfo, promptSnapshot(existingPrompt), promptSnapshot(updatedPrompt, request.content() != null)));

            return GmsPromptResponse.from(updatedPrompt);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private void emitAfterCommit(Runnable auditLog) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditLog.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditLog.run();
            }
        });
    }

    private Map<String, Object> promptSnapshot(GmsPrompt prompt) {
        return promptSnapshot(prompt, false);
    }

    private Map<String, Object> promptSnapshot(GmsPrompt prompt, boolean contentChanged) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", prompt.getId().toString());
        snapshot.put("name", prompt.getName());
        snapshot.put("feature_type", prompt.getFeatureType());
        if (contentChanged) {
            snapshot.put("content_changed", true);
        }

        return snapshot;
    }

    private Map<String, Object> promptDeletedSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deleted", true);

        return snapshot;
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
