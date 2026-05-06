package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.LoginRequest;
import com.nemonicworld.auth.dto.request.LogoutRequest;
import com.nemonicworld.auth.dto.request.TokenRefreshRequest;
import com.nemonicworld.auth.dto.response.LoginResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AuthService {

    LoginResponse login(LoginRequest request, AdminClientInfo clientInfo);

    LoginResponse refreshToken(TokenRefreshRequest request);

    void logout(AdminPrincipal adminPrincipal, LogoutRequest request, String accessToken, AdminClientInfo clientInfo);
}
