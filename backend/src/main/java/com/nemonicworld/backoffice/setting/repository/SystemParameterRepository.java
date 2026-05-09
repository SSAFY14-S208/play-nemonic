package com.nemonicworld.backoffice.setting.repository;

import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class SystemParameterRepository {

    private static final String SELECT_COLUMNS = """
        SELECT s.id,
               s.setting_key,
               s.setting_value,
               s.updated_by,
               a.nickname AS updated_by_nickname,
               s.created_at,
               s.updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public SystemParameterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SystemParameter> findAll(String keyword) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append("""
            FROM backoffice_setting s
            LEFT JOIN admin_user a ON a.id = s.updated_by
            WHERE 1 = 1
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, keyword);
        sql.append("""
            ORDER BY s.setting_key ASC, s.id ASC
            """);

        return jdbcTemplate.query(sql.toString(), this::mapParameter, params.toArray());
    }

    public long countAll(String keyword) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM backoffice_setting s
            WHERE 1 = 1
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, keyword);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());

        return count == null ? 0L : count;
    }

    private void appendSearchConditions(StringBuilder sql, List<Object> params, String keyword) {
        if (StringUtils.hasText(keyword)) {
            sql.append("""
                  AND LOWER(s.setting_key) LIKE ?
                """);
            params.add("%" + keyword + "%");
        }
    }

    private SystemParameter mapParameter(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SystemParameter(resultSet.getLong("id"), resultSet.getString("setting_key"),
            resultSet.getString("setting_value"), resultSet.getObject("updated_by", Long.class),
            resultSet.getString("updated_by_nickname"), timestampToLocalDateTime(resultSet, "created_at"),
            timestampToLocalDateTime(resultSet, "updated_at"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
