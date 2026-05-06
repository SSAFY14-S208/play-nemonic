package com.nemonicworld.gms.service;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.gms.dto.request.GmsPromptCreateRequest;
import com.nemonicworld.gms.dto.response.GmsPromptResponse;

public interface GmsPromptService {

    GmsPromptResponse createPrompt(AdminPrincipal adminPrincipal, GmsPromptCreateRequest request);
}
