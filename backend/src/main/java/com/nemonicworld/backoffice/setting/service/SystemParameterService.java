package com.nemonicworld.backoffice.setting.service;

import com.nemonicworld.backoffice.setting.dto.request.SystemParameterBulkUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface SystemParameterService {

    SystemParameterListResponse getSystemParameters(AdminPrincipal adminPrincipal, String keyword);

    SystemParameterListResponse bulkUpdate(AdminPrincipal adminPrincipal, SystemParameterBulkUpdateRequest request,
        AdminClientInfo clientInfo);
}
