package com.nemonicworld.auth.dto.response;

import com.nemonicworld.auth.entity.AdminUser;

public record AdminResponse(Long id, String loginId, String nickname, String email, String role) {

    public static AdminResponse from(AdminUser adminUser) {
        return new AdminResponse(adminUser.getId(), adminUser.getLoginId(), adminUser.getNickname(),
            adminUser.getEmail(), adminUser.getRole().getValue());
    }
}
