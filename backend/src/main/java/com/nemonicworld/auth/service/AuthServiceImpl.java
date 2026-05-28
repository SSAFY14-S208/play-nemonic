package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.LoginRequest;
import com.nemonicworld.auth.dto.request.LogoutRequest;
import com.nemonicworld.auth.dto.request.TokenRefreshRequest;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.auth.service.session.AdminLoginUseCase;
import com.nemonicworld.auth.service.session.AdminLogoutUseCase;
import com.nemonicworld.auth.service.session.AdminTokenRefreshUseCase;
import com.nemonicworld.common.jwt.AdminPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AdminLoginUseCase adminLoginUseCase;
    private final AdminTokenRefreshUseCase adminTokenRefreshUseCase;
    private final AdminLogoutUseCase adminLogoutUseCase;

    public AuthServiceImpl(AdminLoginUseCase adminLoginUseCase, AdminTokenRefreshUseCase adminTokenRefreshUseCase,
        AdminLogoutUseCase adminLogoutUseCase) {
        this.adminLoginUseCase = adminLoginUseCase;
        this.adminTokenRefreshUseCase = adminTokenRefreshUseCase;
        this.adminLogoutUseCase = adminLogoutUseCase;
    }

    @Override
    public LoginResponse login(LoginRequest request, AdminClientInfo clientInfo) {
        return adminLoginUseCase.login(request, clientInfo);
    }

    @Override
    public LoginResponse refreshToken(TokenRefreshRequest request) {
        return adminTokenRefreshUseCase.refreshToken(request);
    }

    @Override
    public void logout(AdminPrincipal adminPrincipal, LogoutRequest request, String accessToken,
        AdminClientInfo clientInfo) {
        adminLogoutUseCase.logout(adminPrincipal, request, accessToken, clientInfo);
    }
}
