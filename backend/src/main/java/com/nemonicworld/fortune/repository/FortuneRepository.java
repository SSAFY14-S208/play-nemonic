package com.nemonicworld.fortune.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    private FortuneTodayRow mapTodayRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FortuneTodayRow(resultSet.getObject("artifact_id", UUID.class),
            resultSet.getDate("fortune_date").toLocalDate(), resultSet.getTimestamp("created_at").toLocalDateTime());
    }
}
