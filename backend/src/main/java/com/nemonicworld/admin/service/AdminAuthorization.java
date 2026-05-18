package com.nemonicworld.admin.service;

import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;

public final class AdminAuthorization {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";
    private static final String OPERATOR_REQUIRED_MESSAGE = "관리자 작업 권한이 필요합니다.";
    private static final String SUPER_ADMIN_REQUIRED_MESSAGE = "슈퍼 관리자 권한이 필요합니다.";

    private AdminAuthorization() {
    }

    public static void requireAuthenticated(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    public static void requireOperator(AdminPrincipal adminPrincipal) {
        requireAuthenticated(adminPrincipal);
        if (!adminPrincipal.role().canOperateBackoffice()) {
            throw new ForbiddenException(OPERATOR_REQUIRED_MESSAGE);
        }
    }

    public static void requireSuperAdmin(AdminPrincipal adminPrincipal) {
        requireAuthenticated(adminPrincipal);
        if (!adminPrincipal.role().canManageAdminAccounts()) {
            throw new ForbiddenException(SUPER_ADMIN_REQUIRED_MESSAGE);
        }
    }
}
