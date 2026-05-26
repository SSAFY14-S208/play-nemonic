package com.nemonicworld.support;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class ArtifactSubtypeTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public ArtifactSubtypeTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetSubtypeTables() {
        ensureSubtypeTables();
        deleteSubtypeRows();
    }

    public void ensureSubtypeTables() {
        ensureFortuneArtifactTable();
        ensureFlipbookArtifactTable();
        ensureInfiniteCanvasArtifactTable();
        ensurePhoneArtifactTable();
    }

    public void ensureFortuneArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL DEFAULT '{}',
                fortune_image_url VARCHAR(200) NULL,
                user_id UUID,
                fortune_date DATE
            )
            """);
        jdbcTemplate.execute(
            "ALTER TABLE fortune_artifact ADD COLUMN IF NOT EXISTS description VARCHAR(1000) NOT NULL DEFAULT '{}'");
        jdbcTemplate.execute("ALTER TABLE fortune_artifact ADD COLUMN IF NOT EXISTS user_id UUID");
        jdbcTemplate.execute("ALTER TABLE fortune_artifact ADD COLUMN IF NOT EXISTS fortune_date DATE");
    }

    public void ensureFlipbookArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS flipbook_artifact (
                artifact_id UUID PRIMARY KEY,
                gif_url VARCHAR(200) NULL,
                first_image VARCHAR(200) NULL
            )
            """);
    }

    public void ensureInfiniteCanvasArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS infinite_canvas_artifact (
                artifact_id UUID PRIMARY KEY,
                canvas_image_url VARCHAR(200) NULL
            )
            """);
    }

    public void ensurePhoneArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS phone_artifact (
                artifact_id UUID PRIMARY KEY,
                phone_image_url VARCHAR(200) NULL
            )
            """);
    }

    public void deleteSubtypeRows() {
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM flipbook_artifact");
        jdbcTemplate.update("DELETE FROM infinite_canvas_artifact");
        jdbcTemplate.update("DELETE FROM phone_artifact");
    }

    public void insertFortuneArtifact(UUID artifactId, String description, String fortuneImageUrl, UUID userUuid,
        LocalDate fortuneDate) {
        jdbcTemplate.update("""
            INSERT INTO fortune_artifact (
                artifact_id,
                description,
                fortune_image_url,
                user_id,
                fortune_date
            )
            VALUES (?, ?, ?, ?, ?)
            """, artifactId, description, fortuneImageUrl, userUuid, fortuneDate);
    }

    public void insertFlipbookArtifact(UUID artifactId, String gifUrl, String firstImage) {
        jdbcTemplate.update("INSERT INTO flipbook_artifact (artifact_id, gif_url, first_image) VALUES (?, ?, ?)",
            artifactId, gifUrl, firstImage);
    }

    public void insertInfiniteCanvasArtifact(UUID artifactId, String canvasImageUrl) {
        jdbcTemplate.update("INSERT INTO infinite_canvas_artifact (artifact_id, canvas_image_url) VALUES (?, ?)",
            artifactId, canvasImageUrl);
    }

    public void insertPhoneArtifact(UUID artifactId, String phoneImageUrl) {
        jdbcTemplate.update("INSERT INTO phone_artifact (artifact_id, phone_image_url) VALUES (?, ?)", artifactId,
            phoneImageUrl);
    }
}
