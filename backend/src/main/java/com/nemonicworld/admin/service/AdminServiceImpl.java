package com.nemonicworld.admin.service;

import com.nemonicworld.admin.dto.request.AdminCreateRequest;
import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.service.account.AdminAccountCreateUseCase;
import com.nemonicworld.admin.service.account.AdminAccountCredentialUseCase;
import com.nemonicworld.admin.service.account.AdminAccountDeleteUseCase;
import com.nemonicworld.admin.service.account.AdminAccountQueryUseCase;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminAccountCreateUseCase adminAccountCreateUseCase;
    private final AdminAccountQueryUseCase adminAccountQueryUseCase;
    private final AdminAccountCredentialUseCase adminAccountCredentialUseCase;
    private final AdminAccountDeleteUseCase adminAccountDeleteUseCase;

    public AdminServiceImpl(AdminAccountCreateUseCase adminAccountCreateUseCase,
        AdminAccountQueryUseCase adminAccountQueryUseCase, AdminAccountCredentialUseCase adminAccountCredentialUseCase,
        AdminAccountDeleteUseCase adminAccountDeleteUseCase) {
        this.adminAccountCreateUseCase = adminAccountCreateUseCase;
        this.adminAccountQueryUseCase = adminAccountQueryUseCase;
        this.adminAccountCredentialUseCase = adminAccountCredentialUseCase;
        this.adminAccountDeleteUseCase = adminAccountDeleteUseCase;
    }

    @Override
    public AdminResponse createAdmin(AdminPrincipal adminPrincipal, AdminCreateRequest request,
        AdminClientInfo clientInfo) {
        return adminAccountCreateUseCase.createAdmin(adminPrincipal, request, clientInfo);
    }

    @Override
    public List<AdminResponse> findAdmins(AdminPrincipal adminPrincipal) {
        return adminAccountQueryUseCase.findAdmins(adminPrincipal);
    }

    @Override
    public AdminResponse findAdmin(AdminPrincipal adminPrincipal, Long adminId) {
        return adminAccountQueryUseCase.findAdmin(adminPrincipal, adminId);
    }

    @Override
    public void changeAdminPassword(AdminPrincipal adminPrincipal, Long adminId, AdminPasswordChangeRequest request) {
        adminAccountCredentialUseCase.changeAdminPassword(adminPrincipal, adminId, request);
    }

    @Override
    public void deleteAdmin(AdminPrincipal adminPrincipal, Long adminId, AdminClientInfo clientInfo) {
        adminAccountDeleteUseCase.deleteAdmin(adminPrincipal, adminId, clientInfo);
    }
}
