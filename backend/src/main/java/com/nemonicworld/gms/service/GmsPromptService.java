package com.nemonicworld.gms.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.request.GmsPromptUpdateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptListResponse;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;

public interface GmsPromptService {

    GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request,
        AdminClientInfo clientInfo);

    GmsPromptListResponse getPrompts(AdminPrincipal adminPrincipal, String keyword, String featureType, String page,
        String size);

    GmsPromptResponse getPrompt(AdminPrincipal adminPrincipal, Long promptId);

    void deletePrompt(AdminPrincipal adminPrincipal, Long promptId, AdminClientInfo clientInfo);

    GmsPromptResponse updatePrompt(AdminPrincipal adminPrincipal, Long promptId, GmsPromptUpdateRequest request,
        AdminClientInfo clientInfo);
}
