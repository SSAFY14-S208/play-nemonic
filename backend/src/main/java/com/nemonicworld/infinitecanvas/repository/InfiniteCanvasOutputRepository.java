package com.nemonicworld.infinitecanvas.repository;

import java.sql.Timestamp;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InfiniteCanvasOutputRepository {

    private final JdbcTemplate jdbcTemplate;

    public InfiniteCanvasOutputRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(InfiniteCanvasOutputCreateCommand command) {
        insertArtifact(command);
        insertInfiniteCanvasArtifact(command);
        insertGallery(command);
    }

    private void insertArtifact(InfiniteCanvasOutputCreateCommand command) {
        jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement("""
                INSERT INTO artifact (
                    id,
                    kind,
                    source_room_id,
                    thumbnail_url,
                    meta,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """);
            preparedStatement.setObject(1, command.artifactId());
            preparedStatement.setObject(2, command.kind(), Types.OTHER);
            preparedStatement.setString(3, command.roomCode());
            preparedStatement.setString(4, command.thumbnailObjectKey());
            preparedStatement.setString(5, command.meta());
            preparedStatement.setTimestamp(6, Timestamp.valueOf(command.createdAt()));
            preparedStatement.setTimestamp(7, Timestamp.valueOf(command.updatedAt()));

            return preparedStatement;
        });
    }

    private void insertInfiniteCanvasArtifact(InfiniteCanvasOutputCreateCommand command) {
        jdbcTemplate.update("""
            INSERT INTO infinite_canvas_artifact (
                artifact_id,
                canvas_image_url
            )
            VALUES (?, ?)
            """, command.artifactId(), command.imageObjectKey());
    }

    private void insertGallery(InfiniteCanvasOutputCreateCommand command) {
        jdbcTemplate.update("""
            INSERT INTO gallery (
                id,
                user_id,
                artifact_id,
                deleted_at
            )
            VALUES (?, ?, ?, NULL)
            """, command.galleryId(), command.userId(), command.artifactId());
    }
}
