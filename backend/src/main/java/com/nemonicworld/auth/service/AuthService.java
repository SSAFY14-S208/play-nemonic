package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminLoginRequest;
import com.nemonicworld.auth.dto.request.AdminLogoutRequest;
import com.nemonicworld.auth.dto.request.AdminTokenRefreshRequest;
import com.nemonicworld.auth.dto.response.AdminLoginResponse;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AuthService {

    AdminLoginResponse login(AdminLoginRequest request, AdminClientInfo clientInfo);

    AdminLoginResponse refreshToken(AdminTokenRefreshRequest request);

    AdminResponse getCurrentAdmin(AdminPrincipal adminPrincipal);

    void logout(AdminPrincipal adminPrincipal, AdminLogoutRequest request, String accessToken,
        AdminClientInfo clientInfo);
}
