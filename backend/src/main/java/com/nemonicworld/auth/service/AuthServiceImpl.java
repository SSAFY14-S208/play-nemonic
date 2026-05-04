package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminLoginRequest;
import com.nemonicworld.auth.dto.response.AdminLoginResponse;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.auth.entity.AdminUser;
import com.nemonicworld.auth.repository.AdminUserRepository;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.jwt.IssuedAdminToken;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import com.nemonicworld.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String AUTHENTICATION_FAILED_MESSAGE = "관리자 인증에 실패했습니다.";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminAuditLogger adminAuditLogger;

    public AuthServiceImpl(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider, AdminAuditLogger adminAuditLogger) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Override
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, AdminClientInfo clientInfo) {
        AdminUser adminUser = adminUserRepository.findByLoginId(request.loginId()).orElse(null);

        if (adminUser == null || adminUser.isDeleted()
            || !passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            adminAuditLogger.logLoginFailure(request.loginId(), adminUser, clientInfo);
            throw new UnauthorizedException(AUTHENTICATION_FAILED_MESSAGE);
        }

        LocalDateTime loginTime = LocalDateTime.now();
        adminUserRepository.updateLastLoginAt(adminUser.getId(), loginTime);
        AdminUser updatedAdminUser = adminUserRepository.findActiveById(adminUser.getId()).orElse(adminUser);
        IssuedAdminToken issuedToken = jwtTokenProvider.createAccessToken(updatedAdminUser);
        adminAuditLogger.logLoginSuccess(updatedAdminUser, clientInfo);

        return new AdminLoginResponse(issuedToken.accessToken(), TOKEN_TYPE, issuedToken.expiresAt(),
            AdminResponse.from(updatedAdminUser));
    }

    @Override
    public AdminResponse getCurrentAdmin(AdminPrincipal adminPrincipal) {
        return new AdminResponse(adminPrincipal.id(), adminPrincipal.loginId(), adminPrincipal.nickname(),
            adminPrincipal.email(), adminPrincipal.role().getValue());
    }

    @Override
    public void logout(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo) {
        adminAuditLogger.logLogout(adminPrincipal, clientInfo);
    }
}
