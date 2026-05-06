package com.nemonicworld.gms.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.repository.GmsPromptInsertCommand;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GmsPromptServiceImpl implements GmsPromptService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String DUPLICATE_NAME_MESSAGE = "이미 등록된 GMS 프롬프트 이름입니다.";
    private static final String REQUIRED_NAME_MESSAGE = "프롬프트 이름을 입력해야 합니다.";
    private static final String REQUIRED_CONTENT_MESSAGE = "프롬프트 본문을 입력해야 합니다.";
    private static final String REQUIRED_FEATURE_TYPE_MESSAGE = "프롬프트 기능 타입을 입력해야 합니다.";

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
