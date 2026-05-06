package com.nemonicworld.relay.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.service.finalization.RelayFinalizationArtifactResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 최종 결과물을 artifact 계열 테이블과 gallery에 저장합니다.
 */
@Repository
public class RelayArtifactRepository {

    private static final String RELAY_DRAWING_KIND = "relay_drawing";
    private static final String FIND_RELAY_ARTIFACTS_BY_SOURCE_ROOM_ID_SQL = """
        SELECT
            a.id AS artifact_id,
            a.thumbnail_url AS thumbnail_url,
            a.meta AS meta,
            rda.combined_preview_url AS combined_preview_url
        FROM artifact a
        JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
        ORDER BY a.created_at ASC, a.id ASC
        """;
    private static final String INSERT_ARTIFACT_SQL = """
        INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
        VALUES (:id, :kind, :sourceRoomId, :thumbnailUrl, :meta, :createdAt, :updatedAt)
        """;
    private static final String INSERT_RELAY_DRAWING_ARTIFACT_SQL = """
        INSERT INTO relay_drawing_artifact (artifact_id, combined_preview_url)
        VALUES (:artifactId, :combinedPreviewUrl)
        """;
    private static final String INSERT_GALLERY_SQL = """
        INSERT INTO gallery (id, user_id, artifact_id, deleted_at)
        VALUES (:id, :userId, :artifactId, NULL)
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RelayArtifactRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RelayFinalizationArtifactResult> findRelayArtifactsBySourceRoomId(String roomCode) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", RELAY_DRAWING_KIND)
            .addValue("roomCode", roomCode);

        return jdbcTemplate.query(FIND_RELAY_ARTIFACTS_BY_SOURCE_ROOM_ID_SQL, params, this::mapArtifactRow).stream()
            .filter(row -> row.canvasIndex() >= 0)
            .sorted(Comparator.comparingInt(RelayFinalizationArtifactResult::canvasIndex)).toList();
    }

    @Transactional
    public void saveRelayDrawingResults(String roomCode, List<RelayFinalizationArtifactResult> artifacts,
        List<String> participantUuidValues, LocalDateTime now) {
        if (artifacts.isEmpty()) {
            return;
        }

        insertArtifacts(roomCode, artifacts, now);
        insertRelayDrawingArtifacts(artifacts);
        insertGalleryItems(artifacts, participantUuidValues);
    }

    private void insertArtifacts(String roomCode, List<RelayFinalizationArtifactResult> artifacts, LocalDateTime now) {
        MapSqlParameterSource[] params = artifacts.stream()
            .map(artifact -> new MapSqlParameterSource().addValue("id", artifact.artifactId())
                .addValue("kind", RELAY_DRAWING_KIND, Types.OTHER).addValue("sourceRoomId", roomCode)
                .addValue("thumbnailUrl", artifact.thumbnailObjectKey()).addValue("meta", artifact.meta())
                .addValue("createdAt", now).addValue("updatedAt", now))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_ARTIFACT_SQL, params);
    }

    private void insertRelayDrawingArtifacts(List<RelayFinalizationArtifactResult> artifacts) {
        MapSqlParameterSource[] params = artifacts.stream().map(artifact -> new MapSqlParameterSource()
            .addValue("artifactId", artifact.artifactId()).addValue("combinedPreviewUrl", artifact.originalObjectKey()))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_RELAY_DRAWING_ARTIFACT_SQL, params);
    }

    private void insertGalleryItems(List<RelayFinalizationArtifactResult> artifacts,
        List<String> participantUuidValues) {
        List<MapSqlParameterSource> params = new ArrayList<>();
        List<UUID> participantUuids = participantUuidValues.stream().map(UUID::fromString).distinct().toList();

        for (UUID participantUuid : participantUuids) {
            for (RelayFinalizationArtifactResult artifact : artifacts) {
                params.add(new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                    .addValue("userId", participantUuid).addValue("artifactId", artifact.artifactId()));
            }
        }

        jdbcTemplate.batchUpdate(INSERT_GALLERY_SQL, params.toArray(MapSqlParameterSource[]::new));
    }

    private RelayFinalizationArtifactResult mapArtifactRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String meta = resultSet.getString("meta");

        return new RelayFinalizationArtifactResult(resultSet.getObject("artifact_id", UUID.class),
            extractCanvasIndex(meta), resultSet.getString("combined_preview_url"), resultSet.getString("thumbnail_url"),
            meta);
    }

    private int extractCanvasIndex(String meta) {
        try {
            JsonNode metaNode = objectMapper.readTree(meta);
            return metaNode.path("canvasIndex").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }
}
