package com.nemonicworld.community.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final String INSERT_DIRECT_MEMO_SQL = """
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
            NULL,
            :positionX,
            :positionY,
            :zIndex,
            :rotationDeg,
            :decoration,
            :bodyImageUrl,
            :thumbnailImageUrl,
            'allowed',
            :ocrText,
            :ocrCategories,
            :moderationCheckedAt,
            :attachedAt,
            :createdAt,
            :updatedAt
        )
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

    public void insertDirectMemo(CommunityMemoCreateCommand command) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", command.memoId())
            .addValue("userId", command.userId()).addValue("positionX", command.positionX())
            .addValue("positionY", command.positionY()).addValue("zIndex", command.zIndex())
            .addValue("rotationDeg", command.rotationDeg()).addValue("decoration", command.decoration())
            .addValue("bodyImageUrl", command.bodyImageUrl()).addValue("thumbnailImageUrl", command.thumbnailImageUrl())
            .addValue("ocrText", command.ocrText()).addValue("ocrCategories", command.ocrCategories())
            .addValue("moderationCheckedAt", command.moderationCheckedAt()).addValue("attachedAt", command.attachedAt())
            .addValue("createdAt", command.createdAt()).addValue("updatedAt", command.updatedAt());

        jdbcTemplate.update(INSERT_DIRECT_MEMO_SQL, params);
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
