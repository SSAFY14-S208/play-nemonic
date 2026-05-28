package com.nemonicworld.admin.service.account;

import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountCredentialUseCase {

    private static final String ADMIN_ACCOUNT_NOT_FOUND_MESSAGE = "관리자 계정을 찾을 수 없습니다.";
    private static final String SUPER_ADMIN_DELETE_FORBIDDEN_MESSAGE = "슈퍼 관리자 계정은 삭제할 수 없습니다.";

    private final AdminUserRepository adminUserRepository;
    private final AdminTokenStore adminTokenStore;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountCredentialUseCase(AdminUserRepository adminUserRepository, AdminTokenStore adminTokenStore,
        PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.adminTokenStore = adminTokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changeAdminPassword(AdminPrincipal adminPrincipal, Long adminId, AdminPasswordChangeRequest request) {
        AdminAuthorization.requireSuperAdmin(adminPrincipal);

        AdminUser targetAdmin = adminUserRepository.findActiveById(adminId)
            .orElseThrow(() -> new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE));
        if (targetAdmin.getRole() == AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException(SUPER_ADMIN_DELETE_FORBIDDEN_MESSAGE);
        }

        int updatedCount = adminUserRepository.updatePasswordHashById(adminId,
            passwordEncoder.encode(request.password()), LocalDateTime.now());
        if (updatedCount == 0) {
            throw new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE);
        }

        Instant revokedAt = Instant.now();
        adminTokenStore.revokeAllRefreshTokens(adminId);
        adminTokenStore.revokeAccessTokensIssuedBefore(adminId, revokedAt);
    }
}
