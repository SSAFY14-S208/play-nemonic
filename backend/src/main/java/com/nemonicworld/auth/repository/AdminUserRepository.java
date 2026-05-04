package com.nemonicworld.auth.repository;

import com.nemonicworld.auth.entity.AdminRole;
import com.nemonicworld.auth.entity.AdminUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * admin_user 테이블을 조회하고 로그인 메타데이터를 갱신합니다.
 */
@Repository
public class AdminUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AdminUser> findByLoginId(String loginId) {
        return jdbcTemplate.query("""
            SELECT id,
                   login_id,
                   password_hash,
                   nickname,
                   email,
                   role,
                   last_login_at,
                   created_at,
                   updated_at,
                   deleted_at
              FROM admin_user
             WHERE login_id = ?
            """, this::mapAdminUser, loginId).stream().findFirst();
    }

    public Optional<AdminUser> findActiveById(Long id) {
        return jdbcTemplate.query("""
            SELECT id,
                   login_id,
                   password_hash,
                   nickname,
                   email,
                   role,
                   last_login_at,
                   created_at,
                   updated_at,
                   deleted_at
              FROM admin_user
             WHERE id = ?
               AND deleted_at IS NULL
            """, this::mapAdminUser, id).stream().findFirst();
    }

    public void updateLastLoginAt(Long id, LocalDateTime lastLoginAt) {
        jdbcTemplate.update("UPDATE admin_user SET last_login_at = ?, updated_at = ? WHERE id = ?", lastLoginAt,
            lastLoginAt, id);
    }

    private AdminUser mapAdminUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdminUser(resultSet.getLong("id"), resultSet.getString("login_id"),
            resultSet.getString("password_hash"), resultSet.getString("nickname"), resultSet.getString("email"),
            AdminRole.fromValue(resultSet.getString("role")), timestampToLocalDateTime(resultSet, "last_login_at"),
            timestampToLocalDateTime(resultSet, "created_at"), timestampToLocalDateTime(resultSet, "updated_at"),
            timestampToLocalDateTime(resultSet, "deleted_at"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
