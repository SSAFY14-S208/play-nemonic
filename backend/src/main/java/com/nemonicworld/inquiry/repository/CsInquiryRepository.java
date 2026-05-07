package com.nemonicworld.inquiry.repository;

import com.nemonicworld.inquiry.entity.CsInquiry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CsInquiryRepository {

    private static final String SELECT_COLUMNS = """
        SELECT id,
               user_id,
               CAST(inquiry_type AS VARCHAR) AS inquiry_type,
               title,
               content,
               email,
               attachments,
               meta,
               CAST(status AS VARCHAR) AS status,
               created_at,
               updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public CsInquiryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CsInquiry insertInquiry(CsInquiryInsertCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                INSERT INTO cs_inquiry (
                    user_id,
                    inquiry_type,
                    title,
                    content,
                    email,
                    attachments,
                    meta,
                    status,
                    assigned_to,
                    response_note,
                    responded_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
                """, new String[]{"id"});
            preparedStatement.setObject(1, command.userId());
            preparedStatement.setObject(2, command.type(), Types.OTHER);
            preparedStatement.setString(3, command.title());
            preparedStatement.setString(4, command.content());
            preparedStatement.setString(5, command.email());
            preparedStatement.setString(6, command.attachments());
            preparedStatement.setString(7, command.meta());
            preparedStatement.setObject(8, command.status(), Types.OTHER);
            preparedStatement.setTimestamp(9, Timestamp.valueOf(command.createdAt()));
            preparedStatement.setTimestamp(10, Timestamp.valueOf(command.updatedAt()));

            return preparedStatement;
        }, keyHolder);

        Number createdId = keyHolder.getKey();
        return findById(createdId.longValue()).orElseThrow();
    }

    public Optional<CsInquiry> findById(Long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + """
            FROM cs_inquiry
            WHERE id = ?
            """, this::mapInquiry, id).stream().findFirst();
    }

    private CsInquiry mapInquiry(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CsInquiry(resultSet.getLong("id"), resultSet.getObject("user_id", UUID.class),
            resultSet.getString("inquiry_type"), resultSet.getString("title"), resultSet.getString("content"),
            resultSet.getString("email"), resultSet.getString("attachments"), resultSet.getString("meta"),
            resultSet.getString("status"), timestampToLocalDateTime(resultSet, "created_at"),
            timestampToLocalDateTime(resultSet, "updated_at"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
