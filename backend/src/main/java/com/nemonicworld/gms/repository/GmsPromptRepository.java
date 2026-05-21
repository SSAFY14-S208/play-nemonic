package com.nemonicworld.gms.repository;

import com.nemonicworld.gms.entity.GmsPrompt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class GmsPromptRepository {

    private static final String SELECT_COLUMNS = """
        SELECT id,
               prompt_name,
               template_text,
               CAST(feature_type AS VARCHAR) AS feature_type,
               created_by,
               created_at,
               updated_at,
               deleted_at,
               is_active,
               activated_at,
               activated_by
        """;

    private final JdbcTemplate jdbcTemplate;

    public GmsPromptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GmsPrompt> findActiveById(Long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
            FROM gms_prompt_template
            WHERE id = ?
              AND deleted_at IS NULL
            """, this::mapPrompt, id).stream().findFirst();
    }

    public Optional<GmsPrompt> findLatestActiveByFeatureType(String featureType) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
            FROM gms_prompt_template
            WHERE deleted_at IS NULL
              AND LOWER(CAST(feature_type AS VARCHAR)) = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """, this::mapPrompt, featureType).stream().findFirst();
    }

    public Optional<GmsPrompt> findCurrentByFeatureType(String featureType) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
            FROM gms_prompt_template
            WHERE deleted_at IS NULL
              AND is_active = TRUE
              AND LOWER(CAST(feature_type AS VARCHAR)) = ?
            ORDER BY activated_at DESC, id DESC
            LIMIT 1
            """, this::mapPrompt, featureType).stream().findFirst();
    }

    public List<GmsPrompt> findActivePrompts(String keyword, String featureType, String status, int limit,
        long offset) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append("""
            FROM gms_prompt_template
            WHERE deleted_at IS NULL
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, keyword, featureType, status);
        sql.append("""
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """);
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapPrompt, params.toArray());
    }

    public long countActivePrompts(String keyword, String featureType, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM gms_prompt_template
            WHERE deleted_at IS NULL
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, keyword, featureType, status);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());

        return count == null ? 0L : count;
    }

    public boolean existsByName(String name) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gms_prompt_template WHERE prompt_name = ?",
            Long.class, name);

        return count != null && count > 0;
    }

    public GmsPrompt insertPrompt(GmsPromptInsertCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                INSERT INTO gms_prompt_template (
                    prompt_name,
                    template_text,
                    feature_type,
                    created_by,
                    created_at,
                    updated_at,
                    deleted_at,
                    is_active,
                    activated_at,
                    activated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL, FALSE, NULL, NULL)
                """, new String[]{"id"});
            preparedStatement.setString(1, command.name());
            preparedStatement.setString(2, command.content());
            preparedStatement.setObject(3, command.featureType(), Types.OTHER);
            preparedStatement.setLong(4, command.createdBy());
            preparedStatement.setTimestamp(5, Timestamp.valueOf(command.createdAt()));
            preparedStatement.setTimestamp(6, Timestamp.valueOf(command.updatedAt()));

            return preparedStatement;
        }, keyHolder);

        Number createdId = keyHolder.getKey();
        return findActiveById(createdId.longValue()).orElseThrow();
    }

    public int softDeleteById(Long id, LocalDateTime deletedAt) {
        return jdbcTemplate.update("""
            UPDATE gms_prompt_template
               SET deleted_at = ?,
                   is_active = FALSE,
                   updated_at = ?
             WHERE id = ?
               AND deleted_at IS NULL
            """, Timestamp.valueOf(deletedAt), Timestamp.valueOf(deletedAt), id);
    }

    public int updatePrompt(GmsPromptUpdateCommand command) {
        return jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                UPDATE gms_prompt_template
                   SET prompt_name = ?,
                       template_text = ?,
                       feature_type = ?,
                       updated_at = ?
                 WHERE id = ?
                   AND deleted_at IS NULL
                """);
            preparedStatement.setString(1, command.name());
            preparedStatement.setString(2, command.content());
            preparedStatement.setObject(3, command.featureType(), Types.OTHER);
            preparedStatement.setTimestamp(4, Timestamp.valueOf(command.updatedAt()));
            preparedStatement.setLong(5, command.id());

            return preparedStatement;
        });
    }

    public void ensureFeatureStateRow(String featureType, LocalDateTime now) {
        List<String> rows = jdbcTemplate.queryForList("""
            SELECT CAST(feature_type AS VARCHAR)
            FROM gms_prompt_feature_state
            WHERE LOWER(CAST(feature_type AS VARCHAR)) = ?
            """, String.class, featureType);
        if (!rows.isEmpty()) {
            return;
        }

        try {
            jdbcTemplate.update(connection -> {
                var preparedStatement = connection.prepareStatement("""
                    INSERT INTO gms_prompt_feature_state (
                        feature_type,
                        current_prompt_id,
                        updated_by,
                        created_at,
                        updated_at
                    )
                    VALUES (?, NULL, NULL, ?, ?)
                    """);
                preparedStatement.setObject(1, featureType, Types.OTHER);
                preparedStatement.setTimestamp(2, Timestamp.valueOf(now));
                preparedStatement.setTimestamp(3, Timestamp.valueOf(now));

                return preparedStatement;
            });
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Another transaction created the lock row first.
        }
    }

    public void lockFeatureState(String featureType) {
        jdbcTemplate.queryForList("""
            SELECT CAST(feature_type AS VARCHAR)
            FROM gms_prompt_feature_state
            WHERE LOWER(CAST(feature_type AS VARCHAR)) = ?
            FOR UPDATE
            """, String.class, featureType);
    }

    public int deactivateCurrentByFeatureType(String featureType, LocalDateTime updatedAt) {
        return jdbcTemplate.update("""
            UPDATE gms_prompt_template
               SET is_active = FALSE,
                   updated_at = ?
             WHERE deleted_at IS NULL
               AND is_active = TRUE
               AND LOWER(CAST(feature_type AS VARCHAR)) = ?
            """, Timestamp.valueOf(updatedAt), featureType);
    }

    public int activateById(Long id, LocalDateTime activatedAt, Long activatedBy) {
        return jdbcTemplate.update("""
            UPDATE gms_prompt_template
               SET is_active = TRUE,
                   activated_at = ?,
                   activated_by = ?,
                   updated_at = ?
             WHERE id = ?
               AND deleted_at IS NULL
            """, Timestamp.valueOf(activatedAt), activatedBy, Timestamp.valueOf(activatedAt), id);
    }

    public void updateFeatureState(String featureType, Long currentPromptId, Long updatedBy, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
            UPDATE gms_prompt_feature_state
               SET current_prompt_id = ?,
                   updated_by = ?,
                   updated_at = ?
             WHERE LOWER(CAST(feature_type AS VARCHAR)) = ?
            """, currentPromptId, updatedBy, Timestamp.valueOf(updatedAt), featureType);
    }

    private void appendSearchConditions(StringBuilder sql, List<Object> params, String keyword, String featureType,
        String status) {
        if (StringUtils.hasText(keyword)) {
            String keywordPattern = "%" + escapeLikeKeyword(keyword) + "%";
            sql.append("""
                  AND (
                      LOWER(prompt_name) LIKE ? ESCAPE '!'
                      OR LOWER(template_text) LIKE ? ESCAPE '!'
                  )
                """);
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        if (StringUtils.hasText(featureType)) {
            sql.append("""
                  AND LOWER(CAST(feature_type AS VARCHAR)) = ?
                """);
            params.add(featureType);
        }

        if (StringUtils.hasText(status)) {
            if ("active".equals(status)) {
                sql.append("""
                      AND is_active = TRUE
                    """);
            } else if ("not_active".equals(status)) {
                sql.append("""
                      AND is_active = FALSE
                    """);
            }
        }
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private GmsPrompt mapPrompt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GmsPrompt(resultSet.getLong("id"), resultSet.getString("prompt_name"),
            resultSet.getString("template_text"), resultSet.getString("feature_type"), resultSet.getLong("created_by"),
            timestampToLocalDateTime(resultSet, "created_at"), timestampToLocalDateTime(resultSet, "updated_at"),
            timestampToLocalDateTime(resultSet, "deleted_at"), resultSet.getBoolean("is_active"),
            timestampToLocalDateTime(resultSet, "activated_at"), nullableLong(resultSet, "activated_by"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);

        return resultSet.wasNull() ? null : value;
    }
}
