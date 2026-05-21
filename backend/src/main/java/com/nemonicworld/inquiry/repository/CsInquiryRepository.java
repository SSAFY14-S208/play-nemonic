package com.nemonicworld.inquiry.repository;

import com.nemonicworld.inquiry.entity.CsInquiry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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
               assigned_to,
               response_note,
               responded_at,
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

    public List<CsInquiry> findInquiries(String status, String type, String keyword, UUID userUuid, int limit,
        long offset) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append("""
            FROM cs_inquiry
            WHERE 1 = 1
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, status, type, keyword, userUuid);
        sql.append("""
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """);
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapInquiry, params.toArray());
    }

    public long countInquiries(String status, String type, String keyword, UUID userUuid) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM cs_inquiry
            WHERE 1 = 1
            """);
        List<Object> params = new ArrayList<>();
        appendSearchConditions(sql, params, status, type, keyword, userUuid);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());

        return count == null ? 0L : count;
    }

    public int updateReply(Long id, Long assignedTo, String responseNote, LocalDateTime respondedAt, String status) {
        return jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                UPDATE cs_inquiry
                   SET status = ?,
                       assigned_to = ?,
                       response_note = ?,
                       responded_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """);
            preparedStatement.setObject(1, status, Types.OTHER);
            preparedStatement.setLong(2, assignedTo);
            preparedStatement.setString(3, responseNote);
            preparedStatement.setTimestamp(4, Timestamp.valueOf(respondedAt));
            preparedStatement.setTimestamp(5, Timestamp.valueOf(respondedAt));
            preparedStatement.setLong(6, id);

            return preparedStatement;
        });
    }

    public int updateStatus(Long id, String status, LocalDateTime updatedAt) {
        return jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                UPDATE cs_inquiry
                   SET status = ?,
                       updated_at = ?
                 WHERE id = ?
                """);
            preparedStatement.setObject(1, status, Types.OTHER);
            preparedStatement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            preparedStatement.setLong(3, id);

            return preparedStatement;
        });
    }

    private void appendSearchConditions(StringBuilder sql, List<Object> params, String status, String type,
        String keyword, UUID userUuid) {
        if (StringUtils.hasText(status)) {
            sql.append("""
                  AND LOWER(CAST(status AS VARCHAR)) = ?
                """);
            params.add(status);
        }

        if (StringUtils.hasText(type)) {
            sql.append("""
                  AND LOWER(CAST(inquiry_type AS VARCHAR)) = ?
                """);
            params.add(type);
        }

        if (StringUtils.hasText(keyword)) {
            String keywordPattern = "%" + escapeLikeKeyword(keyword) + "%";
            sql.append("""
                  AND (
                      LOWER(title) LIKE ? ESCAPE '!'
                      OR LOWER(content) LIKE ? ESCAPE '!'
                      OR LOWER(email) LIKE ? ESCAPE '!'
                  )
                """);
            params.add(keywordPattern);
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        if (userUuid != null) {
            sql.append("""
                  AND user_id = ?
                """);
            params.add(userUuid);
        }
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private CsInquiry mapInquiry(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CsInquiry(resultSet.getLong("id"), resultSet.getObject("user_id", UUID.class),
            resultSet.getString("inquiry_type"), resultSet.getString("title"), resultSet.getString("content"),
            resultSet.getString("email"), resultSet.getString("attachments"), resultSet.getString("meta"),
            resultSet.getString("status"), resultSet.getObject("assigned_to", Long.class),
            resultSet.getString("response_note"), timestampToLocalDateTime(resultSet, "responded_at"),
            timestampToLocalDateTime(resultSet, "created_at"), timestampToLocalDateTime(resultSet, "updated_at"));
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
