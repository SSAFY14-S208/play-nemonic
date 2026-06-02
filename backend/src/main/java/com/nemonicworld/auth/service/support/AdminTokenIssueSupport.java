package com.nemonicworld.auth.service.support;

import com.nemonicworld.admin.dto.response.AdminResponse;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.auth.service.IssuedAdminRefreshToken;
import com.nemonicworld.common.jwt.IssuedAdminToken;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import org.springframework.stereotype.Component;

@Component
public class AdminTokenIssueSupport {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminTokenStore adminTokenStore;

    public AdminTokenIssueSupport(JwtTokenProvider jwtTokenProvider, AdminTokenStore adminTokenStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminTokenStore = adminTokenStore;
    }

    public LoginResponse issueLoginResponse(AdminUser adminUser) {
        IssuedAdminToken issuedAccessToken = jwtTokenProvider.createAccessToken(adminUser);
        IssuedAdminRefreshToken issuedRefreshToken = adminTokenStore.issueRefreshToken(adminUser);

        return new LoginResponse(issuedAccessToken.accessToken(), TOKEN_TYPE, issuedAccessToken.expiresAt(),
            issuedRefreshToken.refreshToken(), issuedRefreshToken.expiresAt(), AdminResponse.from(adminUser));
    }
}
