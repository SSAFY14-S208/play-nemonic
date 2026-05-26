package com.nemonicworld.support;

import org.springframework.jdbc.core.JdbcTemplate;

public class ArtifactGalleryTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public ArtifactGalleryTestFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetRelayArtifactTables() {
        ensureRelayArtifactTables();
        deleteRelayArtifactRows();
    }

    public void ensureRelayArtifactTables() {
        ensureArtifactTable();
        ensureGalleryTable();
        ensureRelayDrawingArtifactTable();
    }

    public void ensureArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id UUID PRIMARY KEY,
                kind VARCHAR(32) NOT NULL,
                source_room_id VARCHAR(64) NULL,
                thumbnail_url VARCHAR(200) NOT NULL,
                meta VARCHAR(1000) NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
    }

    public void ensureGalleryTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS gallery (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
    }

    public void ensureRelayDrawingArtifactTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
    }

    public void deleteRelayArtifactRows() {
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
    }
}
