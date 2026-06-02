package com.nemonicworld.auth.service.session;

import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.auth.dto.request.LoginRequest;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.support.AdminTokenIssueSupport;
import com.nemonicworld.auth.service.support.AuthAuditAfterCommitSupport;
import com.nemonicworld.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminLoginUseCase {

    private static final String AUTHENTICATION_FAILED_MESSAGE = "관리자 인증에 실패했습니다.";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogger adminAuditLogger;
    private final AdminTokenIssueSupport adminTokenIssueSupport;
    private final AuthAuditAfterCommitSupport authAuditAfterCommitSupport;

    public AdminLoginUseCase(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
        AdminAuditLogger adminAuditLogger, AdminTokenIssueSupport adminTokenIssueSupport,
        AuthAuditAfterCommitSupport authAuditAfterCommitSupport) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuditLogger = adminAuditLogger;
        this.adminTokenIssueSupport = adminTokenIssueSupport;
        this.authAuditAfterCommitSupport = authAuditAfterCommitSupport;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, AdminClientInfo clientInfo) {
        AdminUser adminUser = adminUserRepository.findByLoginId(request.loginId()).orElse(null);

        if (adminUser == null || adminUser.isDeleted()
            || !passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            adminAuditLogger.logLoginFailure(request.loginId(), adminUser, clientInfo);
            throw new UnauthorizedException(AUTHENTICATION_FAILED_MESSAGE);
        }

        LocalDateTime loginTime = LocalDateTime.now();
        adminUserRepository.updateLastLoginAt(adminUser.getId(), loginTime);
        AdminUser updatedAdminUser = adminUserRepository.findActiveById(adminUser.getId()).orElse(adminUser);
        authAuditAfterCommitSupport
            .emitAfterCommit(() -> adminAuditLogger.logLoginSuccess(updatedAdminUser, clientInfo));

        return adminTokenIssueSupport.issueLoginResponse(updatedAdminUser);
    }
}
