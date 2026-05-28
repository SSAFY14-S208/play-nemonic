package com.nemonicworld.admin.service.account;

import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountQueryUseCase {

    private static final String ADMIN_ACCOUNT_NOT_FOUND_MESSAGE = "관리자 계정을 찾을 수 없습니다.";

    private final AdminUserRepository adminUserRepository;

    public AdminAccountQueryUseCase(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> findAdmins(AdminPrincipal adminPrincipal) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        return adminUserRepository.findActiveAll().stream().map(AdminResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AdminResponse findAdmin(AdminPrincipal adminPrincipal, Long adminId) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        AdminUser adminUser = adminUserRepository.findActiveById(adminId)
            .orElseThrow(() -> new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE));

        return AdminResponse.from(adminUser);
    }
}
