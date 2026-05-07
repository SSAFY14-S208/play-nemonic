package com.nemonicworld.gms.repository;

import com.nemonicworld.gms.entity.GmsPrompt;
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
               deleted_at
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
                    deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL)
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

    private GmsPrompt mapPrompt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GmsPrompt(resultSet.getLong("id"), resultSet.getString("prompt_name"),
            resultSet.getString("template_text"), resultSet.getString("feature_type"), resultSet.getLong("created_by"),
            timestampToLocalDateTime(resultSet, "created_at"), timestampToLocalDateTime(resultSet, "updated_at"),
            timestampToLocalDateTime(resultSet, "deleted_at"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
