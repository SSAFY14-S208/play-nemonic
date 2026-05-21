package com.nemonicworld.common.jwt;

import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;

public record AdminPrincipal(Long id, String loginId, String nickname, String email, AdminRole role) {

    public static AdminPrincipal from(AdminUser adminUser) {
        return new AdminPrincipal(adminUser.getId(), adminUser.getLoginId(), adminUser.getNickname(),
            adminUser.getEmail(), adminUser.getRole());
    }
}
