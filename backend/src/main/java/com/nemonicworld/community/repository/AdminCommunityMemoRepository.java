package com.nemonicworld.community.repository;

import com.nemonicworld.community.entity.CommunityMemoHiddenReason;
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
import org.springframework.util.StringUtils;

/**
 * 관리자 운영 화면에서 커뮤니티 메모를 조회하고 숨김 상태를 변경하는 JDBC 저장소입니다.
 */
@Repository
public class AdminCommunityMemoRepository {

    private static final String SELECT_ADMIN_MEMO = """
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
            cm.is_hidden AS is_hidden,
            CAST(cm.hidden_reason AS VARCHAR) AS hidden_reason,
            cm.hidden_at AS hidden_at,
            CAST(cm.moderation_status AS VARCHAR) AS moderation_status,
            cm.ocr_text AS ocr_text,
            cm.ocr_categories AS ocr_categories,
            cm.reviewed_by AS reviewed_by,
            cm.reviewed_at AS reviewed_at,
            cm.attached_at AS attached_at,
            cm.created_at AS created_at,
            cm.updated_at AS updated_at
        FROM community_memo cm
        LEFT JOIN app_user au ON au.id = cm.user_id
        LEFT JOIN artifact a ON a.id = cm.artifact_id
        WHERE cm.deleted_at IS NULL
        """;

    private static final String COUNT_ADMIN_MEMO = """
        SELECT COUNT(*)
        FROM community_memo cm
        LEFT JOIN app_user au ON au.id = cm.user_id
        WHERE cm.deleted_at IS NULL
        """;

    private static final String FIND_ADMIN_MEMO_BY_ID_SQL = SELECT_ADMIN_MEMO + """
          AND cm.id = :memoId
        """;

    private static final String EXISTS_ADMIN_MEMO_BY_ID_SQL = """
        SELECT COUNT(*)
        FROM community_memo
        WHERE id = :memoId
          AND deleted_at IS NULL
        """;

    private static final String SELECT_ADMIN_MEMO_REPORT = """
        SELECT
            r.id AS report_id,
            r.memo_id AS memo_id,
            r.user_id AS reporter_user_id,
            au.nickname AS reporter_nickname,
            CAST(r.reason AS VARCHAR) AS reason,
            r.reason_detail AS reason_detail,
            r.created_at AS created_at
        FROM community_memo_report r
        LEFT JOIN app_user au ON au.id = r.user_id
        WHERE r.memo_id = :memoId
        """;

    private static final String COUNT_ADMIN_MEMO_REPORT = """
        SELECT COUNT(*)
        FROM community_memo_report r
        WHERE r.memo_id = :memoId
        """;

    private static final String HIDE_MEMO_SQL = """
        UPDATE community_memo
        SET is_hidden = TRUE,
            hidden_reason = :hiddenReason,
            hidden_at = :hiddenAt,
            reviewed_by = :adminId,
            reviewed_at = :hiddenAt,
            updated_at = :hiddenAt
        WHERE id = :memoId
          AND deleted_at IS NULL
          AND is_hidden = FALSE
        """;

    private static final String RESTORE_MEMO_SQL = """
        UPDATE community_memo
        SET is_hidden = FALSE,
            hidden_reason = NULL,
            hidden_at = NULL,
            report_count = 0,
            reviewed_by = :adminId,
            reviewed_at = :updatedAt,
            updated_at = :updatedAt
        WHERE id = :memoId
          AND deleted_at IS NULL
          AND is_hidden = TRUE
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminCommunityMemoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 관리자 목록 조회 필터와 페이징 조건에 맞는 커뮤니티 메모를 최신 수정 순으로 조회합니다.
     */
    public List<AdminCommunityMemoRow> findMemos(Boolean hidden, String moderationStatus, String sourceType,
        Boolean reported, String keyword, int size, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterClause(hidden, moderationStatus, sourceType, reported, keyword, params);
        params.addValue("size", size).addValue("offset", offset);

        return jdbcTemplate.query(
            SELECT_ADMIN_MEMO + whereClause
                + " ORDER BY cm.updated_at DESC, cm.created_at DESC, cm.id DESC LIMIT :size OFFSET :offset",
            params, this::mapRow);
    }

    /**
     * 관리자 목록 조회의 전체 결과 수를 계산합니다.
     */
    public long countMemos(Boolean hidden, String moderationStatus, String sourceType, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterClause(hidden, moderationStatus, sourceType, null, keyword, params);
        Long count = jdbcTemplate.queryForObject(COUNT_ADMIN_MEMO + whereClause, params, Long.class);

        return count == null ? 0 : count;
    }

    /**
     * 관리자 목록 조회의 전체 결과 수를 신고 여부 필터까지 반영해 계산합니다.
     */
    public long countMemos(Boolean hidden, String moderationStatus, String sourceType, Boolean reported,
        String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterClause(hidden, moderationStatus, sourceType, reported, keyword, params);
        Long count = jdbcTemplate.queryForObject(COUNT_ADMIN_MEMO + whereClause, params, Long.class);

        return count == null ? 0 : count;
    }

    /**
     * 삭제되지 않은 커뮤니티 메모를 숨김 여부와 관계없이 단건 조회합니다.
     */
    public Optional<AdminCommunityMemoRow> findMemoById(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        List<AdminCommunityMemoRow> rows = jdbcTemplate.query(FIND_ADMIN_MEMO_BY_ID_SQL, params, this::mapRow);

        return rows.stream().findFirst();
    }

    /**
     * 관리자 상세 조회 정책과 동일하게 삭제되지 않은 메모가 존재하는지 확인합니다.
     */
    public boolean existsMemoById(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        Long count = jdbcTemplate.queryForObject(EXISTS_ADMIN_MEMO_BY_ID_SQL, params, Long.class);

        return count != null && count > 0;
    }

    /**
     * 특정 메모에 접수된 신고 내역을 최신 신고 순으로 조회합니다.
     */
    public List<AdminCommunityMemoReportRow> findMemoReports(UUID memoId, String reason, int size, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId).addValue("size", size)
            .addValue("offset", offset);
        String reasonFilter = buildReportReasonFilter(reason, params);

        return jdbcTemplate.query(SELECT_ADMIN_MEMO_REPORT + reasonFilter
            + " ORDER BY r.created_at DESC, r.id DESC LIMIT :size OFFSET :offset", params, this::mapReportRow);
    }

    /**
     * 관리자 메모 상세에 포함할 신고 내역을 최신 신고 순으로 모두 조회합니다.
     */
    public List<AdminCommunityMemoReportRow> findMemoReports(UUID memoId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);

        return jdbcTemplate.query(SELECT_ADMIN_MEMO_REPORT + " ORDER BY r.created_at DESC, r.id DESC", params,
            this::mapReportRow);
    }

    /**
     * 특정 메모의 신고 내역 전체 수를 계산합니다.
     */
    public long countMemoReports(UUID memoId, String reason) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId);
        String reasonFilter = buildReportReasonFilter(reason, params);
        Long count = jdbcTemplate.queryForObject(COUNT_ADMIN_MEMO_REPORT + reasonFilter, params, Long.class);

        return count == null ? 0 : count;
    }

    /**
     * visible 메모를 관리자 수동 숨김 상태로 전환합니다.
     */
    public int hideMemo(UUID memoId, long adminId, LocalDateTime hiddenAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId)
            .addValue("adminId", adminId).addValue("hiddenAt", hiddenAt)
            .addValue("hiddenReason", CommunityMemoHiddenReason.ADMIN_HIDDEN.value(), Types.OTHER);

        return jdbcTemplate.update(HIDE_MEMO_SQL, params);
    }

    /**
     * hidden 메모를 다시 visible 상태로 복구합니다.
     */
    public int restoreMemo(UUID memoId, long adminId, LocalDateTime updatedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("memoId", memoId)
            .addValue("adminId", adminId).addValue("updatedAt", updatedAt);

        return jdbcTemplate.update(RESTORE_MEMO_SQL, params);
    }

    private String buildFilterClause(Boolean hidden, String moderationStatus, String sourceType, Boolean reported,
        String keyword, MapSqlParameterSource params) {
        StringBuilder builder = new StringBuilder();
        if (hidden != null) {
            builder.append(" AND cm.is_hidden = :hidden");
            params.addValue("hidden", hidden);
        }
        if (StringUtils.hasText(moderationStatus)) {
            builder.append(" AND CAST(cm.moderation_status AS VARCHAR) = :moderationStatus");
            params.addValue("moderationStatus", moderationStatus);
        }
        if ("DIRECT".equals(sourceType)) {
            builder.append(" AND cm.artifact_id IS NULL");
        } else if ("GALLERY".equals(sourceType)) {
            builder.append(" AND cm.artifact_id IS NOT NULL");
        }
        if (reported != null) {
            builder.append(reported ? " AND cm.report_count > 0" : " AND cm.report_count = 0");
        }
        if (StringUtils.hasText(keyword)) {
            builder.append("""
                 AND (
                    LOWER(COALESCE(au.nickname, '')) LIKE :keyword ESCAPE '!'
                    OR LOWER(COALESCE(cm.ocr_text, '')) LIKE :keyword ESCAPE '!'
                 )
                """);
            params.addValue("keyword", "%%%s%%".formatted(escapeLikeKeyword(keyword)));
        }

        return builder.toString();
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String buildReportReasonFilter(String reason, MapSqlParameterSource params) {
        if (!StringUtils.hasText(reason)) {
            return "";
        }

        params.addValue("reason", reason);

        return " AND CAST(r.reason AS VARCHAR) = :reason";
    }

    private AdminCommunityMemoRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdminCommunityMemoRow(resultSet.getObject("memo_id", UUID.class),
            resultSet.getObject("user_id", UUID.class), resultSet.getString("author_nickname"),
            resultSet.getObject("artifact_id", UUID.class), resultSet.getString("artifact_kind"),
            resultSet.getString("original_image_reference"), resultSet.getString("thumbnail_image_reference"),
            resultSet.getDouble("position_x"), resultSet.getDouble("position_y"), resultSet.getInt("z_index"),
            resultSet.getFloat("rotation_deg"), resultSet.getString("decoration"), resultSet.getInt("report_count"),
            resultSet.getBoolean("is_hidden"), resultSet.getString("hidden_reason"),
            timestampToLocalDateTime(resultSet, "hidden_at"), resultSet.getString("moderation_status"),
            resultSet.getString("ocr_text"), resultSet.getString("ocr_categories"),
            resultSet.getObject("reviewed_by", Long.class), timestampToLocalDateTime(resultSet, "reviewed_at"),
            resultSet.getTimestamp("attached_at").toLocalDateTime(),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    private AdminCommunityMemoReportRow mapReportRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdminCommunityMemoReportRow(resultSet.getLong("report_id"),
            resultSet.getObject("memo_id", UUID.class), resultSet.getObject("reporter_user_id", UUID.class),
            resultSet.getString("reporter_nickname"), resultSet.getString("reason"),
            resultSet.getString("reason_detail"), resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    private LocalDateTime timestampToLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        var timestamp = resultSet.getTimestamp(columnName);

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
