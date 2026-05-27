package com.nemonicworld.gms.service.prompt;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptPreviewRequest;
import com.nemonicworld.gms.dto.request.GmsPromptTestRequest;
import com.nemonicworld.gms.dto.response.GmsPromptPreviewResponse;
import com.nemonicworld.gms.entity.GmsPrompt;
import com.nemonicworld.gms.repository.GmsPromptRepository;
import com.nemonicworld.gms.service.GmsPromptPreviewService;
import org.springframework.stereotype.Service;

@Service
public class GmsPromptPreviewUseCase {

    private static final String PROMPT_NOT_FOUND_MESSAGE = "GMS 프롬프트를 찾을 수 없습니다.";
    private static final String REQUIRED_TEST_REQUEST_MESSAGE = "프롬프트 테스트 요청 본문을 입력해야 합니다.";

    private final GmsPromptRepository gmsPromptRepository;
    private final GmsPromptPreviewService gmsPromptPreviewService;

    public GmsPromptPreviewUseCase(GmsPromptRepository gmsPromptRepository,
        GmsPromptPreviewService gmsPromptPreviewService) {
        this.gmsPromptRepository = gmsPromptRepository;
        this.gmsPromptPreviewService = gmsPromptPreviewService;
    }

    public GmsPromptPreviewResponse previewPrompt(AdminPrincipal adminPrincipal, GmsPromptPreviewRequest request) {
        AdminAuthorization.requireOperator(adminPrincipal);

        return gmsPromptPreviewService.preview(request);
    }

    public GmsPromptPreviewResponse testPrompt(AdminPrincipal adminPrincipal, Long promptId,
        GmsPromptTestRequest request) {
        AdminAuthorization.requireOperator(adminPrincipal);
        if (request == null) {
            throw new BadRequestException(REQUIRED_TEST_REQUEST_MESSAGE);
        }

        GmsPrompt prompt = gmsPromptRepository.findActiveById(promptId)
            .orElseThrow(() -> new NotFoundException(PROMPT_NOT_FOUND_MESSAGE));

        return gmsPromptPreviewService
            .preview(new GmsPromptPreviewRequest(prompt.getFeatureType(), prompt.getContent(), request.sampleSaju()));
    }
}
