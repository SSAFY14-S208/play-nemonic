package com.nemonicworld.community.repository;

import com.nemonicworld.community.entity.CommunityMemoDeletedReason;
import com.nemonicworld.community.entity.CommunityMemoHiddenReason;
import com.nemonicworld.community.entity.CommunityMemoModerationStatus;
import com.nemonicworld.community.entity.CommunityMemoReportReason;
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
        -- 벽 렌더링 순서는 z-index가 낮은 메모부터, 같은 층에서는 먼저 붙은 메모부터입니다.
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
        -- GALLERY 게시에서는 갤러리 이미지를 복사하지 않고, 소유한 gallery의 artifact만 출처로 연결합니다.
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
            -- 숨김 메모는 노출 개수에서 제외하고, 새 메모가 바로 만료되지 않도록 id 조건을 둡니다.
            ORDER BY attached_at ASC, id ASC
            LIMIT :limit
        )
        """;

    private static final String UPDATE_MEMO_LAYOUT_SQL = """
        -- 위치 수정은 본인 visible 메모만 대상으로 하며, 이미지/출처/데코레이션/검수 정보는 건드리지 않습니다.
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

    private static final String SOFT_DELETE_MEMO_SQL = """
        -- 사용자 삭제는 본인 visible 메모만 soft delete 처리하고, 이미지/파일/갤러리 원본 데이터는 보존합니다.
        UPDATE community_memo
        SET deleted_at = :deletedAt,
            deleted_reason = :deletedReason,
            updated_at = :deletedAt
        WHERE id = :memoId
          AND user_id = :userId
          AND deleted_at IS NULL
          AND is_hidden = FALSE
        """;

    private static final String EXISTS_MEMO_REPORT_SQL = """
        SELECT COUNT(*)
        FROM community_memo_report
        WHERE memo_id = :memoId
          AND user_id = :userId
        """;

    private static final String INSERT_MEMO_REPORT_SQL = """
        INSERT INTO community_memo_report (
            memo_id,
            user_id,
            reason,
            created_at
        ) VALUES (
            :memoId,
            :userId,
            :reason,
            :createdAt
        )
        """;

    private static final String INCREMENT_REPORT_COUNT_SQL = """
        UPDATE community_memo
        SET report_count = report_count + 1
        WHERE id = :memoId
          AND deleted_at IS NULL
          AND is_hidden = FALSE
        """;

    private static final String FIND_REPORT_COUNT_SQL = """
        SELECT report_count
        FROM community_memo
        WHERE id = :memoId
        """;

    private static final String HIDE_MEMO_BY_REPORT_THRESHOLD_SQL = """
        -- 신고 누적 자동 숨김은 soft delete와 구분하기 위해 deleted_at은 건드리지 않습니다.
        UPDATE community_memo
        SET is_hidden = TRUE,
            hidden_reason = :hiddenReason,
            hidden_at = :hiddenAt,
            updated_at = :hiddenAt
        WHERE id = :memoId
          AND deleted_at IS NULL
          AND is_hidden = FALSE
          AND report_count >= :threshold
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 커뮤니티 메모 JDBC 쿼리에 사용할 NamedParameterJdbcTemplate을 주입합니다.
     */
    public CommunityMemoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 공용 벽에 노출 가능한 visible 메모 목록을 z-index와 부착 시각 순서로 조회합니다.
     */
    public List<CommunityMemoRow> findVisibleMemos() {
        return jdbcTemplate.query(FIND_VISIBLE_MEMOS_SQL, new MapSqlParameterSource(), this::mapRow);
    }

    /**
     * 지정한 메모 UUID의 visible 상세 row를 조회합니다.
     */
    public Optional<CommunityMemoDetailRow> findVisibleMemoById(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        List<CommunityMemoDetailRow> rows = jdbcTemplate.query(FIND_VISIBLE_MEMO_DETAIL_SQL, params,
            this::mapDetailRow);

        return rows.stream().findFirst();
    }

    /**
     * GALLERY 게시 출처로 사용할 수 있는 요청자 소유의 활성 갤러리 항목을 조회합니다.
     */
    public Optional<CommunityMemoSourceGalleryRow> findActiveSourceGallery(UUID galleryId, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("galleryId", galleryId).addValue("userId",
            userId);
        List<CommunityMemoSourceGalleryRow> rows = jdbcTemplate.query(FIND_ACTIVE_SOURCE_GALLERY_SQL, params,
            (resultSet, rowNumber) -> new CommunityMemoSourceGalleryRow(resultSet.getObject("gallery_id", UUID.class),
                resultSet.getObject("artifact_id", UUID.class), resultSet.getString("artifact_kind")));

        return rows.stream().findFirst();
    }

    /**
     * 검증과 모더레이션을 통과한 커뮤니티 메모 snapshot row를 저장합니다.
     */
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

    /**
     * 삭제되지 않고 숨김 처리되지 않은 현재 visible 메모 개수를 조회합니다.
     */
    public int countVisibleMemos() {
        Integer count = jdbcTemplate.queryForObject(COUNT_VISIBLE_MEMOS_SQL, new MapSqlParameterSource(),
            Integer.class);

        return count == null ? 0 : count;
    }

    /**
     * 생성 직후 50개를 초과한 visible 메모 중 새 메모를 제외한 오래된 메모를 expired 처리합니다.
     */
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

    /**
     * 본인 visible 메모의 배치 필드와 updated_at만 수정합니다.
     */
    public int updateMemoLayout(UUID memoId, UUID userId, double positionX, double positionY, int zIndex,
        float rotationDeg, LocalDateTime updatedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("userId", userId)
            .addValue("positionX", positionX).addValue("positionY", positionY).addValue("zIndex", zIndex)
            .addValue("rotationDeg", rotationDeg).addValue("updatedAt", updatedAt);

        return jdbcTemplate.update(UPDATE_MEMO_LAYOUT_SQL, params);
    }

    /**
     * 본인 visible 메모를 user_delete 사유로 soft delete 처리합니다.
     */
    public int softDeleteMemo(UUID memoId, UUID userId, LocalDateTime deletedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("userId", userId)
            .addValue("deletedAt", deletedAt)
            .addValue("deletedReason", CommunityMemoDeletedReason.USER_DELETE.value(), Types.OTHER);

        return jdbcTemplate.update(SOFT_DELETE_MEMO_SQL, params);
    }

    /**
     * 같은 사용자가 같은 메모를 이미 신고했는지 확인합니다.
     */
    public boolean existsMemoReport(UUID memoId, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("userId",
            userId);
        Integer count = jdbcTemplate.queryForObject(EXISTS_MEMO_REPORT_SQL, params, Integer.class);

        return count != null && count > 0;
    }

    /**
     * 커뮤니티 메모 신고 row를 저장합니다.
     */
    public void insertMemoReport(UUID memoId, UUID userId, CommunityMemoReportReason reason, LocalDateTime createdAt) {
        // reason도 PostgreSQL enum 컬럼이므로 Types.OTHER로 전달합니다.
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("userId", userId)
            .addValue("reason", reason.value(), Types.OTHER).addValue("createdAt", createdAt);

        jdbcTemplate.update(INSERT_MEMO_REPORT_SQL, params);
    }

    /**
     * visible 메모의 report_count를 원자적으로 1 증가시키고 증가 후 값을 반환합니다.
     */
    public int incrementReportCount(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        int updatedCount = jdbcTemplate.update(INCREMENT_REPORT_COUNT_SQL, params);
        if (updatedCount == 0) {
            return 0;
        }

        Integer reportCount = jdbcTemplate.queryForObject(FIND_REPORT_COUNT_SQL, params, Integer.class);
        return reportCount == null ? 0 : reportCount;
    }

    /**
     * 신고 수가 임계값에 도달한 visible 메모를 자동 숨김 처리합니다.
     */
    public int hideMemoByReportThreshold(UUID memoId, LocalDateTime hiddenAt, int threshold) {
        // hidden_reason은 PostgreSQL enum 컬럼이므로 Types.OTHER로 전달합니다.
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId)
            .addValue("hiddenAt", hiddenAt)
            .addValue("hiddenReason", CommunityMemoHiddenReason.REPORT_THRESHOLD.value(), Types.OTHER)
            .addValue("threshold", threshold);

        return jdbcTemplate.update(HIDE_MEMO_BY_REPORT_THRESHOLD_SQL, params);
    }

    /**
     * 목록 조회 결과 ResultSet을 목록 row projection으로 변환합니다.
     */
    private CommunityMemoRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommunityMemoRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("original_image_reference"),
            resultSet.getString("thumbnail_image_reference"), resultSet.getDouble("position_x"),
            resultSet.getDouble("position_y"), resultSet.getInt("z_index"), resultSet.getFloat("rotation_deg"),
            resultSet.getTimestamp("attached_at").toLocalDateTime());
    }

    /**
     * 상세 조회 결과 ResultSet을 상세 row projection으로 변환합니다.
     */
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
