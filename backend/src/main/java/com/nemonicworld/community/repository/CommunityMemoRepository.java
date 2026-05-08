package com.nemonicworld.community.repository;

import com.nemonicworld.community.entity.CommunityMemoDeletedReason;
import com.nemonicworld.community.entity.CommunityMemoModerationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/**
 * 커뮤니티 캔버스 공용 벽 메모를 기존 community_memo와 artifact 계열 테이블에서 읽어오는 조회 전용
 * Repository입니다.
 */
public class CommunityMemoRepository {

    /*
     * 커뮤니티 벽 렌더링 이미지는 artifact 원본이 아니라 community_memo에 저장된 최종 게시 스냅샷입니다. artifact는
     * 상세의 원본 출처 표시용 kind 조회에만 사용합니다.
     */
    private static final String VISIBLE_MEMO_FROM = """
        FROM community_memo cm
        LEFT JOIN app_user au ON au.id = cm.user_id
        LEFT JOIN artifact a ON a.id = cm.artifact_id
        WHERE cm.deleted_at IS NULL
          AND cm.is_hidden = FALSE
        """;

    private static final String FIND_VISIBLE_MEMOS_SQL = """
        SELECT
            cm.id AS memo_id,
            cm.user_id AS user_id,
            au.nickname AS author_nickname,
            cm.artifact_id AS artifact_id,
            cm.body_image_url AS original_image_reference,
            cm.thumbnail_image_url AS thumbnail_image_reference,
            cm.position_x AS position_x,
            cm.position_y AS position_y,
            cm.z_index AS z_index,
            cm.rotation_deg AS rotation_deg,
            cm.attached_at AS attached_at
        """ + VISIBLE_MEMO_FROM + """
        ORDER BY cm.z_index ASC, cm.attached_at ASC
        """;

    private static final String FIND_VISIBLE_MEMO_DETAIL_SQL = """
        SELECT
            cm.id AS memo_id,
            cm.user_id AS user_id,
            au.nickname AS author_nickname,
            cm.artifact_id AS artifact_id,
            CAST(a.kind AS VARCHAR) AS artifact_kind,
            cm.body_image_url AS original_image_reference,
            cm.thumbnail_image_url AS thumbnail_image_reference,
            cm.position_x AS position_x,
            cm.position_y AS position_y,
            cm.z_index AS z_index,
            cm.rotation_deg AS rotation_deg,
            cm.decoration AS decoration,
            cm.report_count AS report_count,
            CAST(cm.moderation_status AS VARCHAR) AS moderation_status,
            cm.attached_at AS attached_at,
            cm.created_at AS created_at,
            cm.updated_at AS updated_at
        """ + VISIBLE_MEMO_FROM + """
          AND cm.id = :memoId
        """;

    private static final String FIND_ACTIVE_SOURCE_GALLERY_SQL = """
        SELECT
            g.id AS gallery_id,
            g.artifact_id AS artifact_id,
            CAST(a.kind AS VARCHAR) AS artifact_kind
        FROM gallery g
        JOIN artifact a ON a.id = g.artifact_id
        WHERE g.id = :galleryId
          AND g.user_id = :userId
          AND g.deleted_at IS NULL
        """;

    private static final String INSERT_MEMO_SQL = """
        INSERT INTO community_memo (
            id,
            user_id,
            artifact_id,
            position_x,
            position_y,
            z_index,
            rotation_deg,
            decoration,
            body_image_url,
            thumbnail_image_url,
            moderation_status,
            ocr_text,
            ocr_categories,
            moderation_checked_at,
            attached_at,
            created_at,
            updated_at
        ) VALUES (
            :memoId,
            :userId,
            :artifactId,
            :positionX,
            :positionY,
            :zIndex,
            :rotationDeg,
            :decoration,
            :bodyImageUrl,
            :thumbnailImageUrl,
            :moderationStatus,
            :ocrText,
            :ocrCategories,
            :moderationCheckedAt,
            :attachedAt,
            :createdAt,
            :updatedAt
        )
        """;

    private static final String COUNT_VISIBLE_MEMOS_SQL = """
        SELECT COUNT(*)
        FROM community_memo
        WHERE deleted_at IS NULL
          AND is_hidden = FALSE
        """;

    private static final String EXPIRE_OLDEST_VISIBLE_MEMOS_SQL = """
        UPDATE community_memo
        SET deleted_at = :deletedAt,
            deleted_reason = :deletedReason,
            updated_at = :deletedAt
        WHERE id IN (
            SELECT id
            FROM community_memo
            WHERE deleted_at IS NULL
              AND is_hidden = FALSE
              AND id <> :newMemoId
            ORDER BY attached_at ASC, id ASC
            LIMIT :limit
        )
        """;

    private static final String UPDATE_MEMO_LAYOUT_SQL = """
        UPDATE community_memo
        SET position_x = :positionX,
            position_y = :positionY,
            z_index = :zIndex,
            rotation_deg = :rotationDeg,
            updated_at = :updatedAt
        WHERE id = :memoId
          AND user_id = :userId
          AND deleted_at IS NULL
          AND is_hidden = FALSE
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommunityMemoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CommunityMemoRow> findVisibleMemos() {
        return jdbcTemplate.query(FIND_VISIBLE_MEMOS_SQL, new MapSqlParameterSource(), this::mapRow);
    }

    public Optional<CommunityMemoDetailRow> findVisibleMemoById(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        List<CommunityMemoDetailRow> rows = jdbcTemplate.query(FIND_VISIBLE_MEMO_DETAIL_SQL, params,
            this::mapDetailRow);

        return rows.stream().findFirst();
    }

    public Optional<CommunityMemoSourceGalleryRow> findActiveSourceGallery(UUID galleryId, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("galleryId", galleryId).addValue("userId",
            userId);
        List<CommunityMemoSourceGalleryRow> rows = jdbcTemplate.query(FIND_ACTIVE_SOURCE_GALLERY_SQL, params,
            (resultSet, rowNumber) -> new CommunityMemoSourceGalleryRow(resultSet.getObject("gallery_id", UUID.class),
                resultSet.getObject("artifact_id", UUID.class), resultSet.getString("artifact_kind")));

        return rows.stream().findFirst();
    }

    public void insertMemo(CommunityMemoCreateCommand command) {
        // PostgreSQL enum 컬럼은 문자열만 넘기면 타입 추론에 실패할 수 있어 Types.OTHER로 전달합니다.
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", command.memoId())
            .addValue("userId", command.userId()).addValue("artifactId", command.artifactId())
            .addValue("positionX", command.positionX()).addValue("positionY", command.positionY())
            .addValue("zIndex", command.zIndex()).addValue("rotationDeg", command.rotationDeg())
            .addValue("decoration", command.decoration()).addValue("bodyImageUrl", command.bodyImageUrl())
            .addValue("thumbnailImageUrl", command.thumbnailImageUrl()).addValue("ocrText", command.ocrText())
            .addValue("ocrCategories", command.ocrCategories())
            .addValue("moderationStatus", CommunityMemoModerationStatus.ALLOWED.value(), Types.OTHER)
            .addValue("moderationCheckedAt", command.moderationCheckedAt()).addValue("attachedAt", command.attachedAt())
            .addValue("createdAt", command.createdAt()).addValue("updatedAt", command.updatedAt());

        jdbcTemplate.update(INSERT_MEMO_SQL, params);
    }

    public int countVisibleMemos() {
        Integer count = jdbcTemplate.queryForObject(COUNT_VISIBLE_MEMOS_SQL, new MapSqlParameterSource(),
            Integer.class);

        return count == null ? 0 : count;
    }

    public int expireOldestVisibleMemos(UUID newMemoId, LocalDateTime deletedAt, int limit) {
        if (limit <= 0) {
            return 0;
        }

        // deleted_reason도 PostgreSQL enum이므로 insert와 같은 방식으로 타입을 명시합니다.
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("newMemoId", newMemoId)
            .addValue("deletedAt", deletedAt)
            .addValue("deletedReason", CommunityMemoDeletedReason.EXPIRED.value(), Types.OTHER)
            .addValue("limit", limit);

        return jdbcTemplate.update(EXPIRE_OLDEST_VISIBLE_MEMOS_SQL, params);
    }

    public int updateMemoLayout(UUID memoId, UUID userId, double positionX, double positionY, int zIndex,
        float rotationDeg, LocalDateTime updatedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("userId", userId)
            .addValue("positionX", positionX).addValue("positionY", positionY).addValue("zIndex", zIndex)
            .addValue("rotationDeg", rotationDeg).addValue("updatedAt", updatedAt);

        return jdbcTemplate.update(UPDATE_MEMO_LAYOUT_SQL, params);
    }

    private CommunityMemoRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommunityMemoRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("original_image_reference"),
            resultSet.getString("thumbnail_image_reference"), resultSet.getDouble("position_x"),
            resultSet.getDouble("position_y"), resultSet.getInt("z_index"), resultSet.getFloat("rotation_deg"),
            resultSet.getTimestamp("attached_at").toLocalDateTime());
    }

    private CommunityMemoDetailRow mapDetailRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommunityMemoDetailRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("artifact_kind"),
            resultSet.getString("original_image_reference"), resultSet.getString("thumbnail_image_reference"),
            resultSet.getDouble("position_x"), resultSet.getDouble("position_y"), resultSet.getInt("z_index"),
            resultSet.getFloat("rotation_deg"), resultSet.getString("decoration"), resultSet.getInt("report_count"),
            resultSet.getString("moderation_status"), resultSet.getTimestamp("attached_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());
    }
}
