package com.nemonicworld.gallery.phone.repository;

import java.sql.Timestamp;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PhoneDrawingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PhoneDrawingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(PhoneDrawingCreateCommand command) {
        insertArtifact(command);
        insertPhoneArtifact(command);
        insertGallery(command);
    }

    private void insertArtifact(PhoneDrawingCreateCommand command) {
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
                VALUES (?, ?, NULL, ?, ?, ?, ?)
                """);
            preparedStatement.setObject(1, command.artifactId());
            preparedStatement.setObject(2, command.kind(), Types.OTHER);
            preparedStatement.setString(3, command.thumbnailObjectKey());
            preparedStatement.setString(4, command.meta());
            preparedStatement.setTimestamp(5, Timestamp.valueOf(command.createdAt()));
            preparedStatement.setTimestamp(6, Timestamp.valueOf(command.updatedAt()));

            return preparedStatement;
        });
    }

    private void insertPhoneArtifact(PhoneDrawingCreateCommand command) {
        jdbcTemplate.update("""
            INSERT INTO phone_artifact (
                artifact_id,
                phone_image_url
            )
            VALUES (?, ?)
            """, command.artifactId(), command.imageObjectKey());
    }

    private void insertGallery(PhoneDrawingCreateCommand command) {
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
