package com.nemonicworld.artifact.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 갤러리 소유권 기준으로 조회 가능한 산출물 이미지 객체 키를 읽습니다.
 */
@Repository
public class ArtifactImageUrlRepository {

    private static final String FIND_ACTIVE_ARTIFACT_IMAGE_URL_SQL = """
        SELECT
            a.id AS artifact_id,
            CAST(a.kind AS VARCHAR) AS kind,
            a.thumbnail_url AS thumbnail_url,
            fa.fortune_image_url AS fortune_image_url,
            rda.combined_preview_url AS relay_combined_preview_url,
            fba.gif_url AS flipbook_gif_url,
            fba.first_image AS flipbook_first_image_url,
            ica.canvas_image_url AS infinite_canvas_image_url,
            pa.phone_image_url AS phone_image_url,
            cm.body_image_url AS community_memo_original_image_url,
            cm.thumbnail_image_url AS community_memo_thumbnail_image_url
        FROM artifact a
        JOIN gallery g ON g.artifact_id = a.id
        LEFT JOIN fortune_artifact fa ON fa.artifact_id = a.id
        LEFT JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        LEFT JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        LEFT JOIN infinite_canvas_artifact ica ON ica.artifact_id = a.id
        LEFT JOIN phone_artifact pa ON pa.artifact_id = a.id
        LEFT JOIN community_memo cm ON cm.artifact_id = a.id
        WHERE a.id = :artifactId
          AND g.user_id = :userUuid
          AND g.deleted_at IS NULL
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ArtifactImageUrlRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ArtifactImageUrlRow> findActiveArtifactImageUrl(UUID artifactId, UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("artifactId", artifactId)
            .addValue("userUuid", userUuid);

        List<ArtifactImageUrlRow> rows = jdbcTemplate.query(FIND_ACTIVE_ARTIFACT_IMAGE_URL_SQL, params, this::mapRow);

        return rows.stream().findFirst();
    }

    private ArtifactImageUrlRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ArtifactImageUrlRow(resultSet.getObject("artifact_id", UUID.class), resultSet.getString("kind"),
            resultSet.getString("thumbnail_url"), resultSet.getString("fortune_image_url"),
            resultSet.getString("relay_combined_preview_url"), resultSet.getString("flipbook_gif_url"),
            resultSet.getString("flipbook_first_image_url"), resultSet.getString("infinite_canvas_image_url"),
            resultSet.getString("phone_image_url"), resultSet.getString("community_memo_original_image_url"),
            resultSet.getString("community_memo_thumbnail_image_url"));
    }
}
