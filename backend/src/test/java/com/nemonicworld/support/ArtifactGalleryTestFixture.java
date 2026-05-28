package com.nemonicworld.support;

import java.time.LocalDateTime;
import java.util.UUID;
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
        deleteGalleryRows();
        deleteArtifactRows();
    }

    public void deleteGalleryRows() {
        jdbcTemplate.update("DELETE FROM gallery");
    }

    public void deleteArtifactRows() {
        jdbcTemplate.update("DELETE FROM artifact");
    }

    public void insertArtifact(UUID artifactId, String kind, String sourceRoomId, String thumbnailUrl, String meta,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
            INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, artifactId, kind, sourceRoomId, thumbnailUrl, meta, createdAt, updatedAt);
    }

    public void insertGallery(UUID galleryId, UUID userUuid, UUID artifactId, LocalDateTime deletedAt) {
        jdbcTemplate.update("INSERT INTO gallery (id, user_id, artifact_id, deleted_at) VALUES (?, ?, ?, ?)", galleryId,
            userUuid, artifactId, deletedAt);
    }

    public void insertRelayDrawingArtifact(UUID artifactId, String combinedPreviewUrl) {
        jdbcTemplate.update("INSERT INTO relay_drawing_artifact (artifact_id, combined_preview_url) VALUES (?, ?)",
            artifactId, combinedPreviewUrl);
    }
}
