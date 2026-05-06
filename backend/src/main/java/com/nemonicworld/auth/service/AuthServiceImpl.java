package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminLoginRequest;
import com.nemonicworld.auth.dto.request.AdminLogoutRequest;
import com.nemonicworld.auth.dto.request.AdminTokenRefreshRequest;
import com.nemonicworld.auth.dto.response.AdminLoginResponse;
import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import com.nemonicworld.common.jwt.IssuedAdminToken;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String AUTHENTICATION_FAILED_MESSAGE = "관리자 인증에 실패했습니다.";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "인증이 필요합니다.";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminTokenStore adminTokenStore;
    private final AdminAuditLogger adminAuditLogger;

    public AuthServiceImpl(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider, AdminTokenStore adminTokenStore, AdminAuditLogger adminAuditLogger) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminTokenStore = adminTokenStore;
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
        adminAuditLogger.logLoginSuccess(updatedAdminUser, clientInfo);

        return issueLoginResponse(updatedAdminUser);
    }

    @Override
    @Transactional
    public AdminLoginResponse refreshToken(AdminTokenRefreshRequest request) {
        StoredAdminRefreshToken storedToken = adminTokenStore.findRefreshToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));
        AdminUser adminUser = adminUserRepository.findActiveById(storedToken.adminId())
            .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

        adminTokenStore.revokeRefreshToken(request.refreshToken());

        return issueLoginResponse(adminUser);
    }

    @Override
    public AdminResponse getCurrentAdmin(AdminPrincipal adminPrincipal) {
        return new AdminResponse(adminPrincipal.id(), adminPrincipal.loginId(), adminPrincipal.nickname(),
            adminPrincipal.email(), adminPrincipal.role().getValue());
    }

    @Override
    public void logout(AdminPrincipal adminPrincipal, AdminLogoutRequest request, String accessToken,
        AdminClientInfo clientInfo) {
        adminTokenStore.revokeRefreshToken(request.refreshToken());
        AdminTokenClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
        adminTokenStore.blacklistAccessToken(claims);
        adminAuditLogger.logLogout(adminPrincipal, clientInfo);
    }

    private AdminLoginResponse issueLoginResponse(AdminUser adminUser) {
        IssuedAdminToken issuedAccessToken = jwtTokenProvider.createAccessToken(adminUser);
        IssuedAdminRefreshToken issuedRefreshToken = adminTokenStore.issueRefreshToken(adminUser);

        return new AdminLoginResponse(issuedAccessToken.accessToken(), TOKEN_TYPE, issuedAccessToken.expiresAt(),
            issuedRefreshToken.refreshToken(), issuedRefreshToken.expiresAt(), AdminResponse.from(adminUser));
    }
}
