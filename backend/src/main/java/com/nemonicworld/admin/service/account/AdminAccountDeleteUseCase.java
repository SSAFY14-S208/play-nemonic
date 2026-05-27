package com.nemonicworld.admin.service.account;

import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.admin.service.support.AdminAuditAfterCommitSupport;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountDeleteUseCase {

    private static final String ADMIN_ACCOUNT_NOT_FOUND_MESSAGE = "관리자 계정을 찾을 수 없습니다.";
    private static final String SELF_DELETE_FORBIDDEN_MESSAGE = "자기 자신은 삭제할 수 없습니다.";
    private static final String SUPER_ADMIN_DELETE_FORBIDDEN_MESSAGE = "슈퍼 관리자 계정은 삭제할 수 없습니다.";

    private final AdminUserRepository adminUserRepository;
    private final AdminTokenStore adminTokenStore;
    private final AdminAuditLogger adminAuditLogger;
    private final AdminAuditAfterCommitSupport adminAuditAfterCommitSupport;

    public AdminAccountDeleteUseCase(AdminUserRepository adminUserRepository, AdminTokenStore adminTokenStore,
        AdminAuditLogger adminAuditLogger, AdminAuditAfterCommitSupport adminAuditAfterCommitSupport) {
        this.adminUserRepository = adminUserRepository;
        this.adminTokenStore = adminTokenStore;
        this.adminAuditLogger = adminAuditLogger;
        this.adminAuditAfterCommitSupport = adminAuditAfterCommitSupport;
    }

    @Transactional
    public void deleteAdmin(AdminPrincipal adminPrincipal, Long adminId, AdminClientInfo clientInfo) {
        AdminAuthorization.requireSuperAdmin(adminPrincipal);

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
        adminAuditAfterCommitSupport
            .emitAfterCommit(() -> adminAuditLogger.logAdminAccountDelete(adminPrincipal, targetAdmin, clientInfo));
    }
}
