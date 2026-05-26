package com.nemonicworld.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class FileUploadTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public FileUploadTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset() {
        ensureTable();
        deleteAll();
    }

    public void ensureTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS file_upload (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                purpose VARCHAR(32) NOT NULL,
                original_file_name VARCHAR(255) NOT NULL,
                content_type VARCHAR(100) NOT NULL,
                byte_size BIGINT NOT NULL,
                object_key TEXT NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM file_upload");
    }

    public UUID insert(UUID userUuid, String purpose, String originalFileName, String contentType, long byteSize,
        String objectKey, String status, LocalDateTime expiresAt, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime deletedAt) {
        UUID fileId = UUID.randomUUID();
        insert(fileId, userUuid, purpose, originalFileName, contentType, byteSize, objectKey, status, expiresAt,
            createdAt, updatedAt, deletedAt);

        return fileId;
    }

    public void insert(UUID fileId, UUID userUuid, String purpose, String originalFileName, String contentType,
        long byteSize, String objectKey, String status, LocalDateTime expiresAt, LocalDateTime createdAt,
        LocalDateTime updatedAt, LocalDateTime deletedAt) {
        jdbcTemplate.update("""
            INSERT INTO file_upload (
                id,
                user_id,
                purpose,
                original_file_name,
                content_type,
                byte_size,
                object_key,
                status,
                expires_at,
                created_at,
                updated_at,
                deleted_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, fileId, userUuid, purpose, originalFileName, contentType, byteSize, objectKey, status,
            Timestamp.valueOf(expiresAt), Timestamp.valueOf(createdAt), Timestamp.valueOf(updatedAt),
            deletedAt == null ? null : Timestamp.valueOf(deletedAt));
    }
}
