package com.nemonicworld.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class AppUserTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public AppUserTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset() {
        ensureTable();
        deleteAll();
    }

    public void ensureTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS app_user (
                id UUID NOT NULL PRIMARY KEY,
                nickname VARCHAR(10) NOT NULL,
                last_seen_at TIMESTAMP NOT NULL,
                birthday DATE NULL,
                birthtime TIME NULL,
                is_lunar BOOLEAN NULL,
                user_agent TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS birthday DATE");
        jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS birthtime TIME");
        jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_lunar BOOLEAN");
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    public void insertAnonymous(UUID userUuid, String nickname, String userAgent, LocalDateTime lastSeenAt,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
            INSERT INTO app_user (
                id,
                nickname,
                last_seen_at,
                birthday,
                birthtime,
                is_lunar,
                user_agent,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, NULL, NULL, NULL, ?, ?, ?)
            """, userUuid, nickname, Timestamp.valueOf(lastSeenAt), userAgent, Timestamp.valueOf(createdAt),
            Timestamp.valueOf(updatedAt));
    }
}
