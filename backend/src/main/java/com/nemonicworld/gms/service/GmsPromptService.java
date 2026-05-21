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

public interface GmsPromptService {

    GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request,
        AdminClientInfo clientInfo);

    GmsPromptListResponse getPrompts(AdminPrincipal adminPrincipal, String keyword, String featureType, String status,
        String page, String size);

    GmsPromptResponse getPrompt(AdminPrincipal adminPrincipal, Long promptId);

    GmsPromptCurrentResponse getCurrentPrompt(AdminPrincipal adminPrincipal, String featureType);

    GmsPromptPreviewResponse previewPrompt(AdminPrincipal adminPrincipal, GmsPromptPreviewRequest request);

    GmsPromptPreviewResponse testPrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptTestRequest request);

    void deletePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo);

    GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptUpdateRequest request,
        AdminClientInfo clientInfo);

    GmsPromptResponse activatePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo);
}
