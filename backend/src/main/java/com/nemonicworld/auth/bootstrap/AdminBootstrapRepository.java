package com.nemonicworld.auth.bootstrap;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminBootstrapRepository {

    private static final String SUPER_ADMIN_ROLE = "super_admin";

    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByLoginId(String loginId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user WHERE login_id = ?", Integer.class,
            loginId);

        return count != null && count > 0;
    }

    public void insertSuperAdmin(String loginId, String passwordHash, String nickname, String email,
        LocalDateTime now) {
        jdbcTemplate.update("""
            INSERT INTO admin_user (
                login_id,
                password_hash,
                nickname,
                email,
                role,
                last_login_at,
                created_at,
                updated_at,
                deleted_at
            )
            VALUES (?, ?, ?, ?, CAST(? AS admin_role_type), NULL, ?, ?, NULL)
            """, loginId, passwordHash, nickname, email, SUPER_ADMIN_ROLE, now, now);
    }
}
