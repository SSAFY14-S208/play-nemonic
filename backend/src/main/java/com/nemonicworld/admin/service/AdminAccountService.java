package com.nemonicworld.admin.service;

import com.nemonicworld.admin.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AdminAccountService {

    AdminResponse createAdminAccount(AdminPrincipal adminPrincipal, AdminAccountCreateRequest request);

    void deleteAdminAccount(AdminPrincipal adminPrincipal, Long adminId);
}
