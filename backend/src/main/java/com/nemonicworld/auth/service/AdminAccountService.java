package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AdminAccountService {

    AdminResponse createAdminAccount(AdminPrincipal adminPrincipal, AdminAccountCreateRequest request);
}
