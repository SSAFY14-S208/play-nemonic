package com.nemonicworld.auth.service.session;

import com.nemonicworld.auth.dto.request.LogoutRequest;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import org.springframework.stereotype.Service;

@Service
public class AdminLogoutUseCase {

    private final AdminTokenStore adminTokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminAuditLogger adminAuditLogger;

    public AdminLogoutUseCase(AdminTokenStore adminTokenStore, JwtTokenProvider jwtTokenProvider,
        AdminAuditLogger adminAuditLogger) {
        this.adminTokenStore = adminTokenStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminAuditLogger = adminAuditLogger;
    }

    public void logout(AdminPrincipal adminPrincipal, LogoutRequest request, String accessToken,
        AdminClientInfo clientInfo) {
        adminTokenStore.revokeRefreshToken(request.refreshToken());
        AdminTokenClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
        adminTokenStore.blacklistAccessToken(claims);
        adminAuditLogger.logLogout(adminPrincipal, clientInfo);
    }
}
