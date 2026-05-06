package com.nemonicworld.admin.service;

import com.nemonicworld.admin.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.admin.dto.request.AdminPasswordChangeRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final String SUPER_ADMIN_REQUIRED_MESSAGE = "슈퍼 관리자 권한이 필요합니다.";
    private static final String DUPLICATE_LOGIN_ID_MESSAGE = "이미 등록된 관리자 아이디입니다.";

    private static final String ADMIN_ACCOUNT_NOT_FOUND_MESSAGE = "관리자 계정을 찾을 수 없습니다.";
    private static final String SELF_DELETE_FORBIDDEN_MESSAGE = "자기 자신은 삭제할 수 없습니다.";
    private static final String SUPER_ADMIN_DELETE_FORBIDDEN_MESSAGE = "슈퍼 관리자 계정은 삭제할 수 없습니다.";

    private final AdminUserRepository adminUserRepository;
    private final AdminTokenStore adminTokenStore;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountServiceImpl(AdminUserRepository adminUserRepository, AdminTokenStore adminTokenStore,
        PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.adminTokenStore = adminTokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AdminResponse createAdminAccount(AdminPrincipal adminPrincipal, AdminAccountCreateRequest request) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }

        if (adminPrincipal.role() != AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException(SUPER_ADMIN_REQUIRED_MESSAGE);
        }

        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }

        try {
            AdminUser adminUser = adminUserRepository.insertAdmin(request.loginId(),
                passwordEncoder.encode(request.password()), request.nickname(), request.email(), LocalDateTime.now());

            return AdminResponse.from(adminUser);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }
    }

    @Override
    public List<AdminResponse> findAdminAccounts(AdminPrincipal adminPrincipal) {
        requireSuperAdmin(adminPrincipal);

        return adminUserRepository.findActiveAll().stream().map(AdminResponse::from).toList();
    }

    @Override
    public AdminResponse findAdminAccount(AdminPrincipal adminPrincipal, Long adminId) {
        requireSuperAdmin(adminPrincipal);

        AdminUser adminUser = adminUserRepository.findActiveById(adminId)
            .orElseThrow(() -> new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE));

        return AdminResponse.from(adminUser);
    }

    @Override
    @Transactional
    public void changeAdminPassword(AdminPrincipal adminPrincipal, Long adminId, AdminPasswordChangeRequest request) {
        requireSuperAdmin(adminPrincipal);

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

    @Override
    @Transactional
    public void deleteAdminAccount(AdminPrincipal adminPrincipal, Long adminId) {
        requireSuperAdmin(adminPrincipal);

        if (adminPrincipal.id().equals(adminId)) {
            throw new ForbiddenException(SELF_DELETE_FORBIDDEN_MESSAGE);
        }

        AdminUser targetAdmin = adminUserRepository.findActiveById(adminId)
            .orElseThrow(() -> new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE));
        if (targetAdmin.getRole() == AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException(SUPER_ADMIN_DELETE_FORBIDDEN_MESSAGE);
        }

        Instant revokedAt = Instant.now();
        int deletedCount = adminUserRepository.softDeleteById(adminId, LocalDateTime.now());
        if (deletedCount == 0) {
            throw new NotFoundException(ADMIN_ACCOUNT_NOT_FOUND_MESSAGE);
        }

        adminTokenStore.revokeAllRefreshTokens(adminId);
        adminTokenStore.revokeAccessTokensIssuedBefore(adminId, revokedAt);
    }

    private void requireSuperAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }

        if (adminPrincipal.role() != AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException(SUPER_ADMIN_REQUIRED_MESSAGE);
        }
    }
}
