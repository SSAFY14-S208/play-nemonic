package com.nemonicworld.admin.service;

import com.nemonicworld.admin.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;

public interface AdminAccountService {

    AdminResponse createAdminAccount(AdminPrincipal adminPrincipal, AdminAccountCreateRequest request);

    List<AdminResponse> findAdminAccounts(AdminPrincipal adminPrincipal);

    AdminResponse findAdminAccount(AdminPrincipal adminPrincipal, Long adminId);

    void changeAdminPassword(AdminPrincipal adminPrincipal, Long adminId, AdminPasswordChangeRequest request);

    void deleteAdminAccount(AdminPrincipal adminPrincipal, Long adminId);
}
