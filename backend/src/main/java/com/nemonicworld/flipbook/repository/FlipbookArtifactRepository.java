package com.nemonicworld.flipbook.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
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
 * 플립북 최종 결과물을 artifact 계열 테이블과 gallery에 저장합니다.
 */
@Repository
public class FlipbookArtifactRepository {

    private static final String FLIPBOOK_KIND = "flipbook";
    private static final String FIND_FLIPBOOK_ARTIFACTS_BY_SOURCE_ROOM_ID_SQL = """
        SELECT
            a.id AS artifact_id,
            a.thumbnail_url AS thumbnail_url,
            a.meta AS meta,
            fba.gif_url AS gif_url,
            fba.first_image AS first_image
        FROM artifact a
        JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
        ORDER BY a.created_at ASC, a.id ASC
        """;
    private static final String FIND_ACTIVE_FLIPBOOK_RESULTS_SQL = """
        SELECT
            g.id AS gallery_id,
            a.id AS artifact_id,
            a.thumbnail_url AS thumbnail_url,
            a.meta AS meta,
            a.created_at AS created_at,
            fba.gif_url AS gif_url,
            fba.first_image AS first_image
        FROM artifact a
        JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        JOIN gallery g ON g.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
          AND g.user_id = :userUuid
          AND g.deleted_at IS NULL
        ORDER BY a.created_at ASC, a.id ASC
        """;
    private static final String COUNT_FLIPBOOK_RESULTS_BY_SOURCE_ROOM_ID_SQL = """
        SELECT COUNT(*)
        FROM artifact a
        JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        WHERE CAST(a.kind AS VARCHAR) = :kind
          AND a.source_room_id = :roomCode
        """;
    private static final String INSERT_ARTIFACT_SQL = """
        INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
        VALUES (:id, :kind, :sourceRoomId, :thumbnailUrl, :meta, :createdAt, :updatedAt)
        """;
    private static final String INSERT_FLIPBOOK_ARTIFACT_SQL = """
        INSERT INTO flipbook_artifact (artifact_id, gif_url, first_image)
        VALUES (:artifactId, :gifUrl, :firstImage)
        """;
    private static final String INSERT_GALLERY_SQL = """
        INSERT INTO gallery (id, user_id, artifact_id, deleted_at)
        VALUES (:id, :userId, :artifactId, NULL)
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FlipbookArtifactRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 같은 방에서 이미 생성된 flipbook 결과물을 flipbookIndex 순서로 조회합니다.
     */
    public List<FlipbookResultArtifactResult> findFlipbookArtifactsBySourceRoomId(String roomCode) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", FLIPBOOK_KIND).addValue("roomCode",
            roomCode);

        return jdbcTemplate.query(FIND_FLIPBOOK_ARTIFACTS_BY_SOURCE_ROOM_ID_SQL, params, this::mapArtifactRow).stream()
            .filter(row -> row.flipbookIndex() >= 0)
            .sorted(Comparator.comparingInt(FlipbookResultArtifactResult::flipbookIndex)).toList();
    }

    /**
     * 요청 사용자의 활성 gallery row가 있는 플립북 최종 결과만 조회합니다.
     */
    public List<FlipbookResultArtifactRow> findActiveFlipbookResultsByRoomCodeAndUserUuid(String roomCode,
        UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", FLIPBOOK_KIND)
            .addValue("roomCode", roomCode).addValue("userUuid", userUuid);

        return jdbcTemplate.query(FIND_ACTIVE_FLIPBOOK_RESULTS_SQL, params, this::mapFlipbookResultArtifactRow);
    }

    /**
     * 특정 방에서 생성된 플립북 최종 결과물 개수를 gallery 소유권과 무관하게 조회합니다.
     */
    public long countFlipbookResultsByRoomCode(String roomCode) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("kind", FLIPBOOK_KIND).addValue("roomCode",
            roomCode);
        Long count = jdbcTemplate.queryForObject(COUNT_FLIPBOOK_RESULTS_BY_SOURCE_ROOM_ID_SQL, params, Long.class);

        return count == null ? 0L : count;
    }

    /**
     * 최종 결과물, 플립북 하위 결과물, 참여자별 갤러리 지급 row를 한 트랜잭션으로 저장합니다.
     */
    @Transactional
    public void saveFlipbookResults(String roomCode, List<FlipbookResultArtifactResult> artifacts,
        List<String> participantUuidValues, LocalDateTime now) {
        if (artifacts.isEmpty()) {
            return;
        }

        insertArtifacts(roomCode, artifacts, now);
        insertFlipbookArtifacts(artifacts);
        insertGalleryItems(artifacts, participantUuidValues);
    }

    private void insertArtifacts(String roomCode, List<FlipbookResultArtifactResult> artifacts, LocalDateTime now) {
        MapSqlParameterSource[] params = artifacts.stream()
            .map(artifact -> new MapSqlParameterSource().addValue("id", artifact.artifactId())
                .addValue("kind", FLIPBOOK_KIND, Types.OTHER).addValue("sourceRoomId", roomCode)
                .addValue("thumbnailUrl", artifact.thumbnailObjectKey()).addValue("meta", artifact.meta())
                .addValue("createdAt", now).addValue("updatedAt", now))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_ARTIFACT_SQL, params);
    }

    private void insertFlipbookArtifacts(List<FlipbookResultArtifactResult> artifacts) {
        MapSqlParameterSource[] params = artifacts.stream()
            .map(artifact -> new MapSqlParameterSource().addValue("artifactId", artifact.artifactId())
                .addValue("gifUrl", artifact.gifObjectKey()).addValue("firstImage", artifact.firstImageObjectKey()))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_FLIPBOOK_ARTIFACT_SQL, params);
    }

    private void insertGalleryItems(List<FlipbookResultArtifactResult> artifacts, List<String> participantUuidValues) {
        List<MapSqlParameterSource> params = new ArrayList<>();
        List<UUID> participantUuids = participantUuidValues.stream().map(UUID::fromString).distinct().toList();

        for (UUID participantUuid : participantUuids) {
            for (FlipbookResultArtifactResult artifact : artifacts) {
                params.add(new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                    .addValue("userId", participantUuid).addValue("artifactId", artifact.artifactId()));
            }
        }

        jdbcTemplate.batchUpdate(INSERT_GALLERY_SQL, params.toArray(MapSqlParameterSource[]::new));
    }

    private FlipbookResultArtifactResult mapArtifactRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String meta = resultSet.getString("meta");

        return new FlipbookResultArtifactResult(resultSet.getObject("artifact_id", UUID.class),
            extractFlipbookIndex(meta), resultSet.getString("gif_url"), resultSet.getString("first_image"),
            resultSet.getString("thumbnail_url"), meta);
    }

    private FlipbookResultArtifactRow mapFlipbookResultArtifactRow(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new FlipbookResultArtifactRow(resultSet.getObject("gallery_id", UUID.class),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("thumbnail_url"),
            resultSet.getString("gif_url"), resultSet.getString("first_image"), resultSet.getString("meta"),
            resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    private int extractFlipbookIndex(String meta) {
        try {
            JsonNode metaNode = objectMapper.readTree(meta);
            return metaNode.path("flipbookIndex").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }
}
