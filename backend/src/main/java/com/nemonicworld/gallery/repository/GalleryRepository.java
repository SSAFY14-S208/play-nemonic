package com.nemonicworld.gallery.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/**
 * 갤러리 목록을 기존 artifact 계열 테이블에서 읽어오는 조회 전용 Repository입니다.
 */
public class GalleryRepository {

    private static final String ACTIVE_GALLERY_FROM = """
        FROM gallery g
        JOIN artifact a ON a.id = g.artifact_id
        LEFT JOIN fortune_artifact fa ON fa.artifact_id = a.id
        LEFT JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        LEFT JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        LEFT JOIN infinite_canvas_artifact ica ON ica.artifact_id = a.id
        LEFT JOIN phone_artifact pa ON pa.artifact_id = a.id
        WHERE g.user_id = :userUuid
          AND g.deleted_at IS NULL
        """;

    private static final String FIND_ACTIVE_ITEMS_SQL = """
        SELECT
            g.id AS gallery_id,
            a.id AS artifact_id,
            CAST(a.kind AS VARCHAR) AS kind,
            a.thumbnail_url AS thumbnail_url,
            CASE CAST(a.kind AS VARCHAR)
                WHEN 'fortune' THEN COALESCE(fa.fortune_image_url, a.thumbnail_url)
                WHEN 'relay_drawing' THEN COALESCE(rda.combined_preview_url, a.thumbnail_url)
                WHEN 'flipbook' THEN COALESCE(fba.gif_url, a.thumbnail_url)
                WHEN 'infinite_canvas' THEN COALESCE(ica.canvas_image_url, a.thumbnail_url)
                WHEN 'phone' THEN COALESCE(pa.phone_image_url, a.thumbnail_url)
                WHEN 'community_memo' THEN a.thumbnail_url
                ELSE a.thumbnail_url
            END AS content_url,
            a.source_room_id AS source_room_id,
            a.created_at AS created_at
        """ + ACTIVE_GALLERY_FROM + """
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT :limit OFFSET :offset
        """;

    private static final String FIND_ACTIVE_ITEM_DETAIL_SQL = """
        SELECT
            g.id AS gallery_id,
            a.id AS artifact_id,
            CAST(a.kind AS VARCHAR) AS kind,
            a.thumbnail_url AS thumbnail_url,
            CASE CAST(a.kind AS VARCHAR)
                WHEN 'fortune' THEN COALESCE(fa.fortune_image_url, a.thumbnail_url)
                WHEN 'relay_drawing' THEN COALESCE(rda.combined_preview_url, a.thumbnail_url)
                WHEN 'flipbook' THEN COALESCE(fba.gif_url, a.thumbnail_url)
                WHEN 'infinite_canvas' THEN COALESCE(ica.canvas_image_url, a.thumbnail_url)
                WHEN 'phone' THEN COALESCE(pa.phone_image_url, a.thumbnail_url)
                WHEN 'community_memo' THEN a.thumbnail_url
                ELSE a.thumbnail_url
            END AS content_url,
            a.source_room_id AS source_room_id,
            a.meta AS meta,
            a.created_at AS created_at,
            a.updated_at AS updated_at
        """ + ACTIVE_GALLERY_FROM + """
          AND g.id = :galleryId
        """;

    private static final String COUNT_ACTIVE_ITEMS_SQL = "SELECT COUNT(*) " + ACTIVE_GALLERY_FROM;

    private static final String FIND_ACTIVE_DELETE_TARGET_SQL = """
        SELECT
            g.id AS gallery_id,
            g.artifact_id AS artifact_id
        FROM gallery g
        WHERE g.id = :galleryId
          AND g.user_id = :userUuid
          AND g.deleted_at IS NULL
        """;

    private static final String SOFT_DELETE_GALLERY_ITEM_SQL = """
        UPDATE gallery
        SET deleted_at = :deletedAt
        WHERE id = :galleryId
          AND user_id = :userUuid
          AND deleted_at IS NULL
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GalleryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GalleryItemRow> findActiveItemsByUserId(UUID userUuid, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userUuid", userUuid)
            .addValue("limit", limit).addValue("offset", offset);

        return jdbcTemplate.query(FIND_ACTIVE_ITEMS_SQL, params, this::mapRow);
    }

    public long countActiveItemsByUserId(UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userUuid", userUuid);
        Long count = jdbcTemplate.queryForObject(COUNT_ACTIVE_ITEMS_SQL, params, Long.class);

        return count == null ? 0L : count;
    }

    public Optional<GalleryDetailRow> findActiveItemDetail(UUID galleryId, UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("galleryId", galleryId).addValue("userUuid",
            userUuid);

        List<GalleryDetailRow> rows = jdbcTemplate.query(FIND_ACTIVE_ITEM_DETAIL_SQL, params, this::mapDetailRow);

        return rows.stream().findFirst();
    }

    public Optional<GalleryDeleteTargetRow> findActiveDeleteTarget(UUID galleryId, UUID userUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("galleryId", galleryId).addValue("userUuid",
            userUuid);

        List<GalleryDeleteTargetRow> rows = jdbcTemplate.query(FIND_ACTIVE_DELETE_TARGET_SQL, params,
            (resultSet, rowNumber) -> new GalleryDeleteTargetRow(resultSet.getObject("gallery_id", UUID.class),
                resultSet.getObject("artifact_id", UUID.class)));

        return rows.stream().findFirst();
    }

    public int softDeleteGalleryItem(UUID galleryId, UUID userUuid, LocalDateTime deletedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("galleryId", galleryId)
            .addValue("userUuid", userUuid).addValue("deletedAt", deletedAt);

        return jdbcTemplate.update(SOFT_DELETE_GALLERY_ITEM_SQL, params);
    }

    private GalleryItemRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GalleryItemRow(resultSet.getObject("gallery_id", UUID.class),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("kind"),
            resultSet.getString("thumbnail_url"), resultSet.getString("content_url"),
            resultSet.getString("source_room_id"), resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    private GalleryDetailRow mapDetailRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GalleryDetailRow(resultSet.getObject("gallery_id", UUID.class),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("kind"),
            resultSet.getString("thumbnail_url"), resultSet.getString("content_url"),
            resultSet.getString("source_room_id"), resultSet.getString("meta"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    /**
     * 상세 조회 화면이 필요한 artifact 메타데이터와 수정 시각까지 포함하는 내부 행 모델입니다.
     */
    public record GalleryDetailRow(UUID galleryId, UUID artifactId, String kind, String thumbnailUrl, String contentUrl,
        String sourceRoomId, String meta, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 삭제 대상 gallery row의 원본 artifact 식별자를 함께 전달하는 내부 행 모델입니다.
     */
    public record GalleryDeleteTargetRow(UUID galleryId, UUID artifactId) {
    }
}
