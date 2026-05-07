package com.nemonicworld.gms.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
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
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final GmsPromptRepository gmsPromptRepository;

    public GmsPromptServiceImpl(GmsPromptRepository gmsPromptRepository) {
        this.gmsPromptRepository = gmsPromptRepository;
    }

    @Override
    @Transactional
    public GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request) {
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
            return GmsPromptResponse.from(gmsPromptRepository.insertPrompt(command));
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
    }

    @Override
    @Transactional
    public void deletePrompt(AdminPrincipal adminPrincipal, Long promptId) {
        requireAdmin(adminPrincipal);

        gmsPromptRepository.findActiveById(promptId).orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        LocalDateTime deletedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int deletedCount = gmsPromptRepository.softDeleteById(promptId, deletedAt);
        if (deletedCount == 0) {
            throw new NotFoundException(PROMPT_NOT_FOUND_MESSAGE);
        }
    }

    @Override
    @Transactional
    public GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId,
        GmsPromptUpdateRequest request) {
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

            return GmsPromptResponse.from(gmsPromptRepository.findActiveById(promptId).orElseThrow());
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
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
