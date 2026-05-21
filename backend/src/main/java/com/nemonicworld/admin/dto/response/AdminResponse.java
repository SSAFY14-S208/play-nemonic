package com.nemonicworld.admin.dto.response;

import com.nemonicworld.admin.entity.AdminUser;

public record AdminResponse(Long id, String loginId, String nickname, String email, String role) {

    public static AdminResponse from(AdminUser adminUser) {
        return new AdminResponse(adminUser.getId(), adminUser.getLoginId(), adminUser.getNickname(),
            adminUser.getEmail(), adminUser.getRole().getValue());
    }
}
