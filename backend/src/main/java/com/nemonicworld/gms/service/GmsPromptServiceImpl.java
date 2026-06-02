package com.nemonicworld.gms.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptPreviewRequest;
import com.nemonicworld.gms.dto.request.GmsPromptTestRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptCurrentResponse;
import com.nemonicworld.gms.dto.response.GmsPromptListResponse;
import com.nemonicworld.gms.dto.response.GmsPromptPreviewResponse;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;
import com.nemonicworld.gms.service.prompt.GmsPromptCommandUseCase;
import com.nemonicworld.gms.service.prompt.GmsPromptPreviewUseCase;
import com.nemonicworld.gms.service.prompt.GmsPromptQueryUseCase;
import org.springframework.stereotype.Service;

@Service
public class GmsPromptServiceImpl implements GmsPromptService {

    private final GmsPromptCommandUseCase gmsPromptCommandUseCase;
    private final GmsPromptQueryUseCase gmsPromptQueryUseCase;
    private final GmsPromptPreviewUseCase gmsPromptPreviewUseCase;

    public GmsPromptServiceImpl(GmsPromptCommandUseCase gmsPromptCommandUseCase,
        GmsPromptQueryUseCase gmsPromptQueryUseCase, GmsPromptPreviewUseCase gmsPromptPreviewUseCase) {
        this.gmsPromptCommandUseCase = gmsPromptCommandUseCase;
        this.gmsPromptQueryUseCase = gmsPromptQueryUseCase;
        this.gmsPromptPreviewUseCase = gmsPromptPreviewUseCase;
    }

    @Override
    public GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request,
        AdminClientInfo clientInfo) {
        return gmsPromptCommandUseCase.createPrompt(adminPrincipal, request, clientInfo);
    }

    @Override
    public GmsPromptListResponse getPrompts(AdminPrincipal adminPrincipal, String keyword, String featureType,
        String status, String pageValue, String sizeValue) {
        return gmsPromptQueryUseCase.getPrompts(adminPrincipal, keyword, featureType, status, pageValue, sizeValue);
    }

    @Override
    public GmsPromptResponse getPrompt(AdminPrincipal adminPrincipal, Long promptId) {
        return gmsPromptQueryUseCase.getPrompt(adminPrincipal, promptId);
    }

    @Override
    public GmsPromptCurrentResponse getCurrentPrompt(AdminPrincipal adminPrincipal, String featureType) {
        return gmsPromptQueryUseCase.getCurrentPrompt(adminPrincipal, featureType);
    }

    @Override
    public GmsPromptPreviewResponse previewPrompt(AdminPrincipal adminPrincipal, GmsPromptPreviewRequest request) {
        return gmsPromptPreviewUseCase.previewPrompt(adminPrincipal, request);
    }

    @Override
    public GmsPromptPreviewResponse testPrompt(AdminPrincipal adminPrincipal, Long promptId,
        GmsPromptTestRequest request) {
        return gmsPromptPreviewUseCase.testPrompt(adminPrincipal, promptId, request);
    }

    @Override
    public void deletePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo) {
        gmsPromptCommandUseCase.deletePrompt(adminPrincipal, promptId, clientInfo);
    }

    @Override
    public GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptUpdateRequest request,
        AdminClientInfo clientInfo) {
        return gmsPromptCommandUseCase.updatePrompt(adminPrincipal, promptId, request, clientInfo);
    }

    @Override
    public GmsPromptResponse activatePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo) {
        return gmsPromptCommandUseCase.activatePrompt(adminPrincipal, promptId, clientInfo);
    }
}
