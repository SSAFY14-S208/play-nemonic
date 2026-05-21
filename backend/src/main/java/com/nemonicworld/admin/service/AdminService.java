package com.nemonicworld.admin.service;

import com.nemonicworld.admin.dto.request.AdminCreateRequest;
import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;

public interface AdminService {

    AdminResponse createAdmin(AdminPrincipal adminPrincipal, AdminCreateRequest request, AdminClientInfo clientInfo);

    List<AdminResponse> findAdmins(AdminPrincipal adminPrincipal);

    AdminResponse findAdmin(AdminPrincipal adminPrincipal, Long adminId);

    void changeAdminPassword(AdminPrincipal adminPrincipal, Long adminId, AdminPasswordChangeRequest request);

    void deleteAdmin(AdminPrincipal adminPrincipal, Long adminId, AdminClientInfo clientInfo);
}
