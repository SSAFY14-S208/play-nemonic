package com.nemonicworld.auth.service.session;

import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.auth.dto.request.TokenRefreshRequest;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.auth.service.StoredAdminRefreshToken;
import com.nemonicworld.auth.service.support.AdminTokenIssueSupport;
import com.nemonicworld.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTokenRefreshUseCase {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "인증이 필요합니다.";

    private final AdminUserRepository adminUserRepository;
    private final AdminTokenStore adminTokenStore;
    private final AdminTokenIssueSupport adminTokenIssueSupport;

    public AdminTokenRefreshUseCase(AdminUserRepository adminUserRepository, AdminTokenStore adminTokenStore,
        AdminTokenIssueSupport adminTokenIssueSupport) {
        this.adminUserRepository = adminUserRepository;
        this.adminTokenStore = adminTokenStore;
        this.adminTokenIssueSupport = adminTokenIssueSupport;
    }

    @Transactional
    public LoginResponse refreshToken(TokenRefreshRequest request) {
        StoredAdminRefreshToken storedToken = adminTokenStore.findRefreshToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));
        AdminUser adminUser = adminUserRepository.findActiveById(storedToken.adminId())
            .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

        adminTokenStore.revokeRefreshToken(request.refreshToken());

        return adminTokenIssueSupport.issueLoginResponse(adminUser);
    }
}
