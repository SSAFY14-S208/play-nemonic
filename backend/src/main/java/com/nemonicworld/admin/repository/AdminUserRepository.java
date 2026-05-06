package com.nemonicworld.admin.repository;

import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * admin_user 테이블을 조회하고 로그인 메타데이터를 갱신합니다.
 */
@Repository
public class AdminUserRepository {

    private static final String CREATED_ADMIN_ROLE = "admin";

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

    public boolean existsByLoginId(String loginId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user WHERE login_id = ?", Integer.class,
            loginId);

        return count != null && count > 0;
    }

    public AdminUser insertAdmin(String loginId, String passwordHash, String nickname, String email,
        LocalDateTime now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
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
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NULL)
                """, new String[]{"id"});
            preparedStatement.setString(1, loginId);
            preparedStatement.setString(2, passwordHash);
            preparedStatement.setString(3, nickname);
            preparedStatement.setString(4, email);
            preparedStatement.setObject(5, CREATED_ADMIN_ROLE, Types.OTHER);
            preparedStatement.setTimestamp(6, Timestamp.valueOf(now));
            preparedStatement.setTimestamp(7, Timestamp.valueOf(now));

            return preparedStatement;
        }, keyHolder);

        Number createdId = keyHolder.getKey();
        if (createdId != null) {
            return findActiveById(createdId.longValue()).orElseThrow();
        }

        return findByLoginId(loginId).orElseThrow();
    }

    public int softDeleteById(Long id, LocalDateTime deletedAt) {
        return jdbcTemplate.update("""
            UPDATE admin_user
               SET deleted_at = ?,
                   updated_at = ?
             WHERE id = ?
               AND deleted_at IS NULL
            """, deletedAt, deletedAt, id);
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
