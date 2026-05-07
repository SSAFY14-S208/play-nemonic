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
    private static final String FIND_ACTIVE_RELAY_RESULTS_SQL = """
        SELECT
            g.id AS gallery_id,
            a.id AS artifact_id,
            a.thumbnail_url AS thumbnail_url,
            a.meta AS meta,
            a.created_at AS created_at,
            rda.combined_preview_url AS content_url
        FROM artifact a
        JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        JOIN gallery g ON g.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
          AND g.user_id = :userUuid
          AND g.deleted_at IS NULL
        ORDER BY a.created_at ASC, a.id ASC
        """;
    private static final String COUNT_RELAY_RESULTS_BY_SOURCE_ROOM_ID_SQL = """
        SELECT COUNT(*)
        FROM artifact a
        JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
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

    /**
     * 같은 방에서 이미 생성된 relay_drawing 결과물을 canvasIndex 순서로 조회합니다.
     */
    public List<RelayFinalizationArtifactResult> findRelayArtifactsBySourceRoomId(String roomCode) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", RELAY_DRAWING_KIND)
            .addValue("roomCode", roomCode);

        return jdbcTemplate.query(FIND_RELAY_ARTIFACTS_BY_SOURCE_ROOM_ID_SQL, params, this::mapArtifactRow).stream()
            .filter(row -> row.canvasIndex() >= 0)
            .sorted(Comparator.comparingInt(RelayFinalizationArtifactResult::canvasIndex)).toList();
    }

    /**
     * 최종 결과물, 릴레이 하위 결과물, 참여자별 갤러리 지급 row를 한 트랜잭션으로 저장합니다.
     */
    /**
     * 요청 사용자의 활성 gallery row가 있는 릴레이 최종 결과만 조회합니다.
     */
    public List<RelayResultArtifactRow> findActiveRelayResultsByRoomCodeAndUserUuid(String roomCode, UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", RELAY_DRAWING_KIND)
            .addValue("roomCode", roomCode).addValue("userUuid", userUuid);

        return jdbcTemplate.query(FIND_ACTIVE_RELAY_RESULTS_SQL, params, this::mapRelayResultArtifactRow);
    }

    /**
     * 특정 방에서 생성된 릴레이 최종 결과물 개수를 gallery 소유권과 무관하게 조회합니다.
     */
    public long countRelayResultsByRoomCode(String roomCode) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", RELAY_DRAWING_KIND)
            .addValue("roomCode", roomCode);
        Long count = jdbcTemplate.queryForObject(COUNT_RELAY_RESULTS_BY_SOURCE_ROOM_ID_SQL, params, Long.class);

        return count == null ? 0L : count;
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

    /**
     * canvasIndex별 artifact 공통 row를 저장합니다.
     */
    private void insertArtifacts(String roomCode, List<RelayFinalizationArtifactResult> artifacts, LocalDateTime now) {
        MapSqlParameterSource[] params = artifacts.stream()
            .map(artifact -> new MapSqlParameterSource().addValue("id", artifact.artifactId())
                .addValue("kind", RELAY_DRAWING_KIND, Types.OTHER).addValue("sourceRoomId", roomCode)
                .addValue("thumbnailUrl", artifact.thumbnailObjectKey()).addValue("meta", artifact.meta())
                .addValue("createdAt", now).addValue("updatedAt", now))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_ARTIFACT_SQL, params);
    }

    /**
     * 릴레이 결과물 원본 objectKey를 relay_drawing_artifact에 저장합니다.
     */
    private void insertRelayDrawingArtifacts(List<RelayFinalizationArtifactResult> artifacts) {
        MapSqlParameterSource[] params = artifacts.stream().map(artifact -> new MapSqlParameterSource()
            .addValue("artifactId", artifact.artifactId()).addValue("combinedPreviewUrl", artifact.originalObjectKey()))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_RELAY_DRAWING_ARTIFACT_SQL, params);
    }

    /**
     * 모든 참여자에게 모든 canvasIndex 결과물을 갤러리 항목으로 지급합니다.
     */
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

    /**
     * DB row와 artifact.meta의 canvasIndex를 최종화 결과 DTO로 변환합니다.
     */
    private RelayFinalizationArtifactResult mapArtifactRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String meta = resultSet.getString("meta");

        return new RelayFinalizationArtifactResult(resultSet.getObject("artifact_id", UUID.class),
            extractCanvasIndex(meta), resultSet.getString("combined_preview_url"), resultSet.getString("thumbnail_url"),
            meta);
    }

    /**
     * artifact.meta에서 canvasIndex를 읽어 기존 결과물 재사용 여부를 판단할 수 있게 합니다.
     */
    private RelayResultArtifactRow mapRelayResultArtifactRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RelayResultArtifactRow(resultSet.getObject("gallery_id", UUID.class),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("thumbnail_url"),
            resultSet.getString("content_url"), resultSet.getString("meta"),
            resultSet.getTimestamp("created_at").toLocalDateTime());
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
