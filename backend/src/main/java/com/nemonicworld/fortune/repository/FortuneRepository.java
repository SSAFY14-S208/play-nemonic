package com.nemonicworld.fortune.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 오늘의 운세 산출물 저장 상태를 조회하는 Repository입니다.
 */
@Repository
public class FortuneRepository {

    private static final String FIND_TODAY_FORTUNE_SQL = """
        SELECT
            fa.artifact_id AS artifact_id,
            fa.fortune_date AS fortune_date,
            a.created_at AS created_at
        FROM fortune_artifact fa
        JOIN artifact a ON a.id = fa.artifact_id
        WHERE fa.user_id = :userUuid
          AND fa.fortune_date = :fortuneDate
        ORDER BY a.created_at DESC, fa.artifact_id DESC
        LIMIT 1
        """;
    private static final String INSERT_ARTIFACT_SQL = """
        INSERT INTO artifact (id, kind, source_room_id, thumbnail_url, meta, created_at, updated_at)
        VALUES (:id, :kind, NULL, :thumbnailUrl, :meta, :createdAt, :updatedAt)
        """;
    private static final String INSERT_FORTUNE_ARTIFACT_SQL = """
        INSERT INTO fortune_artifact (
            artifact_id,
            description,
            fortune_image_url,
            user_id,
            fortune_date
        )
        VALUES (
            :artifactId,
            :description,
            :fortuneImageUrl,
            :userId,
            :fortuneDate
        )
        """;
    private static final String INSERT_GALLERY_SQL = """
        INSERT INTO gallery (id, user_id, artifact_id, deleted_at)
        VALUES (:id, :userId, :artifactId, NULL)
        """;
    private static final String FORTUNE_KIND = "fortune";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FortuneRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 사용자와 KST 날짜 기준으로 이미 생성된 오늘의 운세 산출물을 조회합니다.
     */
    public Optional<FortuneTodayRow> findTodayFortune(UUID userUuid, LocalDate fortuneDate) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userUuid", userUuid)
            .addValue("fortuneDate", fortuneDate);

        List<FortuneTodayRow> rows = jdbcTemplate.query(FIND_TODAY_FORTUNE_SQL, params, this::mapTodayRow);

        return rows.stream().findFirst();
    }

    /**
     * 운세 산출물 공통 row, 운세 상세 row, 생성자 갤러리 row를 저장합니다.
     */
    public void saveFortune(FortuneCreateCommand command) {
        insertArtifact(command);
        insertFortuneArtifact(command);
        insertGallery(command);
    }

    private void insertArtifact(FortuneCreateCommand command) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", command.artifactId())
            .addValue("kind", FORTUNE_KIND, Types.OTHER).addValue("thumbnailUrl", command.fortuneImageObjectKey())
            .addValue("meta", command.artifactMeta()).addValue("createdAt", command.createdAt())
            .addValue("updatedAt", command.createdAt());

        jdbcTemplate.update(INSERT_ARTIFACT_SQL, params);
    }

    private void insertFortuneArtifact(FortuneCreateCommand command) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("artifactId", command.artifactId())
            .addValue("description", command.description(), Types.OTHER)
            .addValue("fortuneImageUrl", command.fortuneImageObjectKey()).addValue("userId", command.userId())
            .addValue("fortuneDate", command.fortuneDate());

        jdbcTemplate.update(INSERT_FORTUNE_ARTIFACT_SQL, params);
    }

    private void insertGallery(FortuneCreateCommand command) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", command.galleryId())
            .addValue("userId", command.userId()).addValue("artifactId", command.artifactId());

        jdbcTemplate.update(INSERT_GALLERY_SQL, params);
    }

    private FortuneTodayRow mapTodayRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FortuneTodayRow(resultSet.getObject("artifact_id", UUID.class),
            resultSet.getDate("fortune_date").toLocalDate(), resultSet.getTimestamp("created_at").toLocalDateTime());
    }
}
