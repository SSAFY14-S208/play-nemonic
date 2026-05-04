package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminLoginRequest;
import com.nemonicworld.auth.dto.response.AdminLoginResponse;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.common.jwt.AdminPrincipal;

public interface AuthService {

    AdminLoginResponse login(AdminLoginRequest request, AdminClientInfo clientInfo);

    AdminResponse getCurrentAdmin(AdminPrincipal adminPrincipal);

    void logout(AdminPrincipal adminPrincipal, AdminClientInfo clientInfo);
}
