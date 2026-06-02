package com.nemonicworld.support;

import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.common.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class BackofficeAuthTestFixture {

    private static final String ENCODED_PASSWORD = "encoded";

    private BackofficeAuthTestFixture() {
    }

    public static String bearerAccessToken(JwtTokenProvider jwtTokenProvider, long id, String loginId, String nickname,
        String email, AdminRole role) {
        return "Bearer %s"
            .formatted(jwtTokenProvider.createAccessToken(adminUser(id, loginId, nickname, email, role)).accessToken());
    }

    public static AdminUser adminUser(long id, String loginId, String nickname, String email, AdminRole role) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new AdminUser(id, loginId, ENCODED_PASSWORD, nickname, email, role, null, now, now, null);
    }
}
