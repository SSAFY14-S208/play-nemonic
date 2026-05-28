package com.nemonicworld.backoffice.setting.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.service.parameter.SystemParameterQueryUseCase;
import com.nemonicworld.backoffice.setting.service.parameter.SystemParameterUpdateUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class SystemParameterServiceImpl implements SystemParameterService {

    private final SystemParameterQueryUseCase systemParameterQueryUseCase;
    private final SystemParameterUpdateUseCase systemParameterUpdateUseCase;

    public SystemParameterServiceImpl(SystemParameterQueryUseCase systemParameterQueryUseCase,
        SystemParameterUpdateUseCase systemParameterUpdateUseCase) {
        this.systemParameterQueryUseCase = systemParameterQueryUseCase;
        this.systemParameterUpdateUseCase = systemParameterUpdateUseCase;
    }

    @Override
    public SystemParameterListResponse getSystemParameters(AdminPrincipal adminPrincipal, String keyword) {
        return systemParameterQueryUseCase.getSystemParameters(adminPrincipal, keyword);
    }

    @Override
    public SystemParameterListResponse bulkUpdate(AdminPrincipal adminPrincipal,
        SystemParameterTypedUpdateRequest request, AdminClientInfo clientInfo) {
        return systemParameterUpdateUseCase.bulkUpdate(adminPrincipal, request, clientInfo);
    }
}
