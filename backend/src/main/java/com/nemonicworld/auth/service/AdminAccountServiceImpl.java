package com.nemonicworld.auth.service;

import com.nemonicworld.auth.dto.request.AdminAccountCreateRequest;
import com.nemonicworld.auth.dto.response.AdminResponse;
import com.nemonicworld.auth.entity.AdminRole;
import com.nemonicworld.auth.entity.AdminUser;
import com.nemonicworld.auth.repository.AdminUserRepository;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final String SUPER_ADMIN_REQUIRED_MESSAGE = "슈퍼 관리자 권한이 필요합니다.";
    private static final String DUPLICATE_LOGIN_ID_MESSAGE = "이미 등록된 관리자 아이디입니다.";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountServiceImpl(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AdminResponse createAdminAccount(AdminPrincipal adminPrincipal, AdminAccountCreateRequest request) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }

        if (adminPrincipal.role() != AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException(SUPER_ADMIN_REQUIRED_MESSAGE);
        }

        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }

        try {
            AdminUser adminUser = adminUserRepository.insertAdmin(request.loginId(),
                passwordEncoder.encode(request.password()), request.nickname(), request.email(), LocalDateTime.now());

            return AdminResponse.from(adminUser);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(DUPLICATE_LOGIN_ID_MESSAGE);
        }
    }
}
