package com.nemonicworld.backoffice.logs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.logs.config.AdminLogsProperties;
import com.nemonicworld.backoffice.logs.dto.request.LogsFieldSummaryRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilter;
import com.nemonicworld.backoffice.logs.dto.request.LogsSearchRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsTimeRange;
import com.nemonicworld.backoffice.logs.exception.AdminLogsException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogsQueryBuilderTest {

    private static final LogsTimeRange ONE_DAY = new LogsTimeRange("2026-05-14T00:00:00Z", "2026-05-15T00:00:00Z");

    private LogsQueryBuilder queryBuilder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        queryBuilder = new LogsQueryBuilder(objectMapper,
            new AdminLogsProperties("http://opensearch:9200", 200, 15_000L));
    }

    @Test
    void emptyQueryBuildsMatchAll() {
        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildSearchQuery(searchRequest("", 50));

        assertThat(built.body().at("/query/bool/must/0/match_all").isObject()).isTrue();
    }

    @Test
    void negatedFilterBuildsMustNotTerm() {
        LogsSearchRequest request = new LogsSearchRequest("biz-events", "",
            List.of(new LogsFilter("service", "api-server", true)), ONE_DAY, 50, null);

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildSearchQuery(request);

        assertThat(built.body().at("/query/bool/must_not/0/term/service").asText()).isEqualTo("api-server");
    }

    @Test
    void resolvesWhitelistedIndexPattern() {
        assertThat(queryBuilder.resolveIndexPattern("all"))
            .isEqualTo("biz-events-*,error-logs-*,access-logs-*,system-logs-*,audit-logs-*");
    }

    @Test
    void rejectsInvalidIndex() {
        assertThatThrownBy(() -> queryBuilder.resolveIndexPattern(".kibana*")).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_INDEX));
    }

    @Test
    void rejectsSizeAboveConfiguredMaximum() {
        assertThatThrownBy(() -> queryBuilder.buildSearchQuery(searchRequest("", 201)))
            .isInstanceOf(AdminLogsException.class).satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> queryBuilder.buildSearchQuery(searchRequest("", 0)))
            .isInstanceOf(AdminLogsException.class).satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void rejectsTimeRangeLongerThanThirtyDays() {
        LogsSearchRequest request = new LogsSearchRequest("biz-events", "", List.of(),
            new LogsTimeRange("2026-05-01T00:00:00Z", "2026-06-01T00:00:01Z"), 50, null);

        assertThatThrownBy(() -> queryBuilder.buildSearchQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(error -> assertThat(((AdminLogsException) error).code())
                .isEqualTo(AdminLogsException.INVALID_TIME_RANGE));
    }

    @Test
    void rejectsDangerousScriptQueryCaseInsensitive() {
        assertThatThrownBy(() -> queryBuilder.buildSearchQuery(searchRequest("level:ERROR AND SCRIPT_score:foo", 50)))
            .isInstanceOf(AdminLogsException.class).satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void allowsScriptTextWhenItIsNotAWordToken() {
        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildSearchQuery(searchRequest("message:manuscript", 50));

        assertThat(built.body().at("/query/bool/must/0/query_string/query").asText()).isEqualTo("message:manuscript");
    }

    @Test
    void rejectsRawOpenSearchDslKeys() throws Exception {
        assertThatThrownBy(() -> queryBuilder.validateRawRequest(objectMapper.readTree("""
            {
              "index": "all",
              "runtime_mappings": {}
            }
            """))).isInstanceOf(AdminLogsException.class).satisfies(
            error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void rejectsInvalidFieldSummaryField() {
        LogsFieldSummaryRequest request = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("service", "uuid"));

        assertThatThrownBy(() -> queryBuilder.buildFieldSummaryQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    private LogsSearchRequest searchRequest(String query, int size) {
        return new LogsSearchRequest("biz-events", query, List.of(), ONE_DAY, size, null);
    }
}
