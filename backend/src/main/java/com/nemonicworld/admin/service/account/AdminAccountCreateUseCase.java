package com.nemonicworld.admin.service.account;

import com.nemonicworld.admin.dto.request.AdminCreateRequest;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.admin.service.support.AdminAuditAfterCommitSupport;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAccountCreateUseCase {

    private static final String DUPLICATE_LOGIN_ID_MESSAGE = "이미 등록된 관리자 아이디입니다.";
    private static final String CREATE_ROLE_NOT_ALLOWED_MESSAGE = "생성할 수 없는 관리자 권한입니다.";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogger adminAuditLogger;
    private final AdminAuditAfterCommitSupport adminAuditAfterCommitSupport;

    public AdminAccountCreateUseCase(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
        AdminAuditLogger adminAuditLogger, AdminAuditAfterCommitSupport adminAuditAfterCommitSupport) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuditLogger = adminAuditLogger;
        this.adminAuditAfterCommitSupport = adminAuditAfterCommitSupport;
    }

    @Transactional
    public AdminResponse createAdmin(AdminPrincipal adminPrincipal, AdminCreateRequest request,
        AdminClientInfo clientInfo) {
        AdminAuthorization.requireSuperAdmin(adminPrincipal);

        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }

        AdminRole createdRole = resolveCreateRole(request.role());
        try {
            AdminUser adminUser = adminUserRepository.insertAdmin(request.loginId(),
                passwordEncoder.encode(request.password()), request.nickname(), request.email(), createdRole,
                LocalDateTime.now());
            adminAuditAfterCommitSupport
                .emitAfterCommit(() -> adminAuditLogger.logAdminAccountCreate(adminPrincipal, adminUser, clientInfo));

            return AdminResponse.from(adminUser);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }
    }

    private AdminRole resolveCreateRole(String roleValue) {
        if (!StringUtils.hasText(roleValue)) {
            return AdminRole.ADMIN;
        }

        AdminRole role = AdminRole.fromValue(roleValue.trim());
        if (!role.canBeCreatedBySuperAdmin()) {
            throw new BadRequestException(CREATE_ROLE_NOT_ALLOWED_MESSAGE);
        }

        return role;
    }
}
