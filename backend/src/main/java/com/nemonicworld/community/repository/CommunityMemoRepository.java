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
     * 목록/상세 조회가 같은 대표 이미지 선택 정책을 쓰도록 SQL 조각으로 공유합니다. subtype URL이 빈 문자열인 기존 row도
     * thumbnail fallback을 타도록 NULLIF로 비웁니다.
     */
    private static final String IMAGE_REFERENCE_SQL = """
        CASE
            WHEN cm.artifact_id IS NULL THEN cm.body_image_url
            ELSE
                CASE CAST(a.kind AS VARCHAR)
                    WHEN 'fortune' THEN COALESCE(NULLIF(fa.fortune_image_url, ''), a.thumbnail_url)
                    WHEN 'relay_drawing' THEN COALESCE(NULLIF(rda.combined_preview_url, ''), a.thumbnail_url)
                    WHEN 'flipbook' THEN COALESCE(NULLIF(fba.gif_url, ''), a.thumbnail_url)
                    WHEN 'infinite_canvas' THEN COALESCE(NULLIF(ica.canvas_image_url, ''), a.thumbnail_url)
                    WHEN 'phone' THEN COALESCE(NULLIF(pa.phone_image_url, ''), a.thumbnail_url)
                    WHEN 'community_memo' THEN a.thumbnail_url
                    ELSE a.thumbnail_url
                END
        END
        """;

    /*
     * V2 이후 community_memo의 FK가 느슨할 수 있어 작성자와 artifact 계열은 LEFT JOIN으로 둡니다. 비정상 로컬
     * 데이터가 있어도 조회 API가 500으로 터지지 않고 null 필드로 내려가게 하기 위함입니다.
     */
    private static final String VISIBLE_MEMO_FROM = """
        FROM community_memo cm
        LEFT JOIN app_user au ON au.id = cm.user_id
        LEFT JOIN artifact a ON a.id = cm.artifact_id
        LEFT JOIN fortune_artifact fa ON fa.artifact_id = a.id
        LEFT JOIN relay_drawing_artifact rda ON rda.artifact_id = a.id
        LEFT JOIN flipbook_artifact fba ON fba.artifact_id = a.id
        LEFT JOIN infinite_canvas_artifact ica ON ica.artifact_id = a.id
        LEFT JOIN phone_artifact pa ON pa.artifact_id = a.id
        WHERE cm.deleted_at IS NULL
          AND cm.is_hidden = FALSE
        """;

    private static final String FIND_VISIBLE_MEMOS_SQL = """
        SELECT
            cm.id AS memo_id,
            cm.user_id AS user_id,
            au.nickname AS author_nickname,
            cm.artifact_id AS artifact_id,
            """ + IMAGE_REFERENCE_SQL + """
            AS image_reference,
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
            """ + IMAGE_REFERENCE_SQL + """
            AS image_reference,
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
            .addValue("bodyImageUrl", command.bodyImageUrl()).addValue("attachedAt", command.attachedAt())
            .addValue("createdAt", command.createdAt()).addValue("updatedAt", command.updatedAt());

        jdbcTemplate.update(INSERT_DIRECT_MEMO_SQL, params);
    }

    private CommunityMemoRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommunityMemoRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("image_reference"),
            resultSet.getDouble("position_x"), resultSet.getDouble("position_y"), resultSet.getInt("z_index"),
            resultSet.getFloat("rotation_deg"), resultSet.getTimestamp("attached_at").toLocalDateTime());
    }

    private CommunityMemoDetailRow mapDetailRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommunityMemoDetailRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("artifact_kind"),
            resultSet.getString("image_reference"), resultSet.getDouble("position_x"),
            resultSet.getDouble("position_y"), resultSet.getInt("z_index"), resultSet.getFloat("rotation_deg"),
            resultSet.getString("decoration"), resultSet.getInt("report_count"),
            resultSet.getString("moderation_status"), resultSet.getTimestamp("attached_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());
    }
}
