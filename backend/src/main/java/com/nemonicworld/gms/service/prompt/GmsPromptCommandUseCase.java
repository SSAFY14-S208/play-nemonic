package com.nemonicworld.gms.service.prompt;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptInsertCommand;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import com.nemonicworld.gms.repository.GmsPromptUpdateCommand;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class GmsPromptCommandUseCase {

    private static final String PROMPT_NOT_FOUND_MESSAGE = "GMS 프롬프트를 찾을 수 없습니다.";
    private static final String DUPLICATE_NAME_MESSAGE = "이미 등록된 GMS 프롬프트 이름입니다.";
    private static final String REQUIRED_NAME_MESSAGE = "프롬프트 이름을 입력해야 합니다.";
    private static final String REQUIRED_CONTENT_MESSAGE = "프롬프트 본문을 입력해야 합니다.";
    private static final String REQUIRED_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입을 입력해야 합니다.";
    private static final String REQUIRED_UPDATE_FIELD_MESSAGE = "수정할 프롬프트 정보를 하나 이상 입력해야 합니다.";
    private static final String ACTIVE_FEATURE_TYPE_CHANGE_MESSAGE = "활성화된 프롬프트는 기능 타입을 변경할 수 없습니다.";

    private final GmsPromptRepository gmsPromptRepository;
    private final AdminAuditLogger adminAuditLogger;

    public GmsPromptCommandUseCase(GmsPromptRepository gmsPromptRepository, AdminAuditLogger adminAuditLogger) {
        this.gmsPromptRepository = gmsPromptRepository;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Transactional
    public GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

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

    @Transactional
    public void deletePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

        GmsPrompt existingPrompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));
        LocalDateTime deletedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        if (existingPrompt.isActive()) {
            gmsPromptRepository.ensureFeatureStateRow(existingPrompt.getFeatureType(), deletedAt);
            gmsPromptRepository.lockFeatureState(existingPrompt.getFeatureType());
        }

        int deletedCount = gmsPromptRepository.softDeleteById(promptId, deletedAt);
        if (deletedCount == 0) {
            throw new NotFoundException(PROMPT_NOT_FOUND_MESSAGE);
        }
        if (existingPrompt.isActive()) {
            gmsPromptRepository.updateFeatureState(existingPrompt.getFeatureType(), null, adminPrincipal.id(),
                deletedAt);
        }

        emitAfterCommit(() -> adminAuditLogger.logPromptUpdate(adminPrincipal, promptId.toString(), "delete",
            clientInfo, promptSnapshot(existingPrompt), promptDeletedSnapshot()));
    }

    @Transactional
    public GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptUpdateRequest request,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);
        if (request == null || request.name() == null && request.content() == null && request.featureType() == null) {
            throw new BadRequestException(REQUIRED_UPDATE_FIELD_MESSAGE);
        }

        GmsPrompt existingPrompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        String name = resolveUpdateName(request, existingPrompt);
        String content = resolveUpdateContent(request, existingPrompt);
        String featureType = resolveUpdateFeatureType(request, existingPrompt);
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

    @Transactional
    public GmsPromptResponse activatePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

        GmsPrompt targetPrompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        gmsPromptRepository.ensureFeatureStateRow(targetPrompt.getFeatureType(), now);
        gmsPromptRepository.lockFeatureState(targetPrompt.getFeatureType());

        GmsPrompt previousPrompt = gmsPromptRepository.findCurrentByFeatureType(targetPrompt.getFeatureType())
            .orElse(null);
        gmsPromptRepository.deactivateCurrentByFeatureType(targetPrompt.getFeatureType(), now);
        int activatedCount = gmsPromptRepository.activateById(promptId, now, adminPrincipal.id());
        if (activatedCount == 0) {
            throw new NotFoundException(PROMPT_NOT_FOUND_MESSAGE);
        }
        gmsPromptRepository.updateFeatureState(targetPrompt.getFeatureType(), promptId, adminPrincipal.id(), now);

        GmsPrompt activatedPrompt = gmsPromptRepository.findActiveById(promptId).orElseThrow();
        emitAfterCommit(() -> adminAuditLogger.logPromptUpdate(adminPrincipal, promptId.toString(), "activate",
            clientInfo, promptSnapshot(previousPrompt), promptSnapshot(activatedPrompt)));

        return GmsPromptResponse.from(activatedPrompt);
    }

    private String resolveUpdateName(GmsPromptUpdateRequest request, GmsPrompt existingPrompt) {
        String name = existingPrompt.getName();
        if (request.name() == null) {
            return name;
        }

        name = normalizeRequiredTrimmed(request.name(), REQUIRED_NAME_MESSAGE);
        if (!existingPrompt.getName().equals(name) && gmsPromptRepository.existsByName(name)) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }

        return name;
    }

    private String resolveUpdateContent(GmsPromptUpdateRequest request, GmsPrompt existingPrompt) {
        if (request.content() == null) {
            return existingPrompt.getContent();
        }

        return normalizeRequired(request.content(), REQUIRED_CONTENT_MESSAGE);
    }

    private String resolveUpdateFeatureType(GmsPromptUpdateRequest request, GmsPrompt existingPrompt) {
        if (request.featureType() == null) {
            return existingPrompt.getFeatureType();
        }

        String featureType = normalizeRequiredTrimmed(request.featureType(), REQUIRED_FEATURE_TYPE_MESSAGE)
            .toLowerCase(Locale.ROOT);
        if (existingPrompt.isActive() && !existingPrompt.getFeatureType().equals(featureType)) {
            throw new BadRequestException(ACTIVE_FEATURE_TYPE_CHANGE_MESSAGE);
        }

        return featureType;
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
        if (prompt == null) {
            return snapshot;
        }

        snapshot.put("id", prompt.getId().toString());
        snapshot.put("name", prompt.getName());
        snapshot.put("feature_type", prompt.getFeatureType());
        snapshot.put("active", prompt.isActive());
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
}
