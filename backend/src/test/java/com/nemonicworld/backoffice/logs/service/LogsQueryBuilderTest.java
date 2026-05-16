package com.nemonicworld.backoffice.logs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.logs.config.AdminLogsProperties;
import com.nemonicworld.backoffice.logs.dto.request.LogsCompositeBucketsRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsDistinctCountRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFieldSummaryRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilter;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilteredMetricGroup;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilteredMetricsRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsHistogramRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsMetricSpec;
import com.nemonicworld.backoffice.logs.dto.request.LogsSearchRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsSubFilter;
import com.nemonicworld.backoffice.logs.dto.request.LogsTermsWithMetricRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsTermsWithSubsRequest;
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
            List.of("service", "uuid"), null);

        assertThatThrownBy(() -> queryBuilder.buildFieldSummaryQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void fieldSummaryAcceptsExtendedMetadataFields() {
        LogsFieldSummaryRequest request = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("metadata.funnel_name", "metadata.step_name", "metadata.entry_type", "metadata.referrer", "path",
                "prev_path"),
            null);

        LogsQueryBuilder.BuiltMsearchQuery built = queryBuilder.buildFieldSummaryQuery(request);

        assertThat(built.fields()).containsExactly("metadata.funnel_name", "metadata.step_name", "metadata.entry_type",
            "metadata.referrer", "path", "prev_path");
        assertThat(built.body()).contains("\"field\":\"metadata.funnel_name\"");
    }

    @Test
    void fieldSummaryUsesRequestedSizeAcrossAllFields() {
        LogsFieldSummaryRequest request = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("service", "event_name"), 50);

        LogsQueryBuilder.BuiltMsearchQuery built = queryBuilder.buildFieldSummaryQuery(request);

        long sizeCount = built.body().lines().filter(line -> line.contains("\"size\":50")).count();
        assertThat(sizeCount).isEqualTo(2);
    }

    @Test
    void fieldSummaryDefaultsToSizeTenWhenNotSpecified() {
        LogsFieldSummaryRequest request = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("service"), null);

        LogsQueryBuilder.BuiltMsearchQuery built = queryBuilder.buildFieldSummaryQuery(request);

        assertThat(built.body()).contains("\"size\":10");
    }

    @Test
    void fieldSummaryRejectsSizeOutOfRange() {
        LogsFieldSummaryRequest tooLarge = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("service"), 101);
        LogsFieldSummaryRequest tooSmall = new LogsFieldSummaryRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("service"), 0);

        assertThatThrownBy(() -> queryBuilder.buildFieldSummaryQuery(tooLarge)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
        assertThatThrownBy(() -> queryBuilder.buildFieldSummaryQuery(tooSmall)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void histogramWithoutGroupByOmitsByFieldAgg() {
        LogsHistogramRequest request = new LogsHistogramRequest("biz-events", "", List.of(), ONE_DAY, null);

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildHistogramQuery(request, "5m");

        assertThat(built.body().at("/aggs/ts/aggs/by_field").isMissingNode()).isTrue();
        assertThat(built.body().at("/aggs/ts/aggs/level/terms/field").asText()).isEqualTo("level");
    }

    @Test
    void histogramWithGroupByAddsByFieldAgg() {
        LogsHistogramRequest request = new LogsHistogramRequest("biz-events", "", List.of(), ONE_DAY, "event_name");

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildHistogramQuery(request, "5m");

        assertThat(built.body().at("/aggs/ts/aggs/by_field/terms/field").asText()).isEqualTo("event_name");
        assertThat(built.body().at("/aggs/ts/aggs/by_field/terms/size").asInt()).isEqualTo(20);
        assertThat(built.body().at("/aggs/ts/aggs/by_field/terms/missing").asText()).isEqualTo("_missing");
    }

    @Test
    void histogramRejectsGroupByOutsideWhitelist() {
        LogsHistogramRequest request = new LogsHistogramRequest("biz-events", "", List.of(), ONE_DAY, "uuid");

        assertThatThrownBy(() -> queryBuilder.buildHistogramQuery(request, "5m")).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void termsWithSubsBuildsNamedFilterAggs() {
        LogsTermsWithSubsRequest request = new LogsTermsWithSubsRequest("biz-events", "", List.of(), ONE_DAY,
            "metadata.funnel_name", 10, List.of(new LogsSubFilter("started", "event_name:funnel_step_completed"),
                new LogsSubFilter("completed", "event_name:funnel_goal_reached")));

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildTermsWithSubsQuery(request);

        assertThat(built.body().at("/aggs/groups/terms/field").asText()).isEqualTo("metadata.funnel_name");
        assertThat(built.body().at("/aggs/groups/terms/size").asInt()).isEqualTo(10);
        assertThat(built.body().at("/aggs/groups/aggs/started/filter/query_string/query").asText())
            .isEqualTo("event_name:funnel_step_completed");
        assertThat(built.body().at("/aggs/groups/aggs/completed/filter/query_string/query").asText())
            .isEqualTo("event_name:funnel_goal_reached");
    }

    @Test
    void termsWithSubsRejectsGroupByOutsideWhitelist() {
        LogsTermsWithSubsRequest request = new LogsTermsWithSubsRequest("biz-events", "", List.of(), ONE_DAY,
            "metadata.step_name", 10, List.of(new LogsSubFilter("ok", "event_name:abc")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithSubsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void termsWithSubsRejectsTooManySubFilters() {
        LogsTermsWithSubsRequest request = new LogsTermsWithSubsRequest("biz-events", "", List.of(), ONE_DAY,
            "event_name", 10,
            List.of(new LogsSubFilter("a", "x:1"), new LogsSubFilter("b", "x:1"), new LogsSubFilter("c", "x:1"),
                new LogsSubFilter("d", "x:1"), new LogsSubFilter("e", "x:1"), new LogsSubFilter("f", "x:1")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithSubsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void termsWithSubsRejectsDangerousSubFilterQuery() {
        LogsTermsWithSubsRequest request = new LogsTermsWithSubsRequest("biz-events", "", List.of(), ONE_DAY,
            "event_name", 10, List.of(new LogsSubFilter("bad", "SCRIPT_score:foo")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithSubsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void termsWithSubsRejectsInvalidSubFilterName() {
        LogsTermsWithSubsRequest request = new LogsTermsWithSubsRequest("biz-events", "", List.of(), ONE_DAY,
            "event_name", 10, List.of(new LogsSubFilter("bad name!", "x:1")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithSubsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void termsWithMetricBuildsMetricAggsOrderedByFirstMetric() {
        LogsTermsWithMetricRequest request = new LogsTermsWithMetricRequest("biz-events", "", List.of(), ONE_DAY,
            "path", 30, List.of(new LogsMetricSpec("avg", "metadata.time_on_page_ms", "avgMs")));

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildTermsWithMetricQuery(request);

        assertThat(built.body().at("/aggs/groups/terms/field").asText()).isEqualTo("path");
        assertThat(built.body().at("/aggs/groups/terms/size").asInt()).isEqualTo(30);
        assertThat(built.body().at("/aggs/groups/terms/order/avgMs").asText()).isEqualTo("desc");
        assertThat(built.body().at("/aggs/groups/aggs/avgMs/avg/field").asText()).isEqualTo("metadata.time_on_page_ms");
    }

    @Test
    void termsWithMetricRejectsMetricFieldOutsideWhitelist() {
        LogsTermsWithMetricRequest request = new LogsTermsWithMetricRequest("biz-events", "", List.of(), ONE_DAY,
            "path", 30, List.of(new LogsMetricSpec("avg", "uuid", "x")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithMetricQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void termsWithMetricRejectsUnknownMetricType() {
        LogsTermsWithMetricRequest request = new LogsTermsWithMetricRequest("biz-events", "", List.of(), ONE_DAY,
            "path", 30, List.of(new LogsMetricSpec("p99", "metadata.time_on_page_ms", "x")));

        assertThatThrownBy(() -> queryBuilder.buildTermsWithMetricQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void compositeBucketsBuildsCompositeAggWithSources() throws Exception {
        JsonNode after = objectMapper.readTree("""
            {"metadata.funnel_name": "relay_room_creation", "metadata.step_name": "settings"}
            """);
        LogsCompositeBucketsRequest request = new LogsCompositeBucketsRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("metadata.funnel_name", "metadata.step_name"), 200, after,
            List.of(new LogsMetricSpec("min", "metadata.step_index", "minStepIndex")));

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildCompositeBucketsQuery(request);

        assertThat(built.body().at("/aggs/composite_buckets/composite/size").asInt()).isEqualTo(200);
        JsonNode sources = built.body().at("/aggs/composite_buckets/composite/sources");
        assertThat(sources.isArray()).isTrue();
        assertThat(sources.size()).isEqualTo(2);
        assertThat(sources.get(0).at("/metadata.funnel_name/terms/field").asText()).isEqualTo("metadata.funnel_name");
        assertThat(built.body().at("/aggs/composite_buckets/composite/after/metadata.funnel_name").asText())
            .isEqualTo("relay_room_creation");
        assertThat(built.body().at("/aggs/composite_buckets/aggs/minStepIndex/min/field").asText())
            .isEqualTo("metadata.step_index");
    }

    @Test
    void compositeBucketsRejectsWrongSourceCount() {
        LogsCompositeBucketsRequest request = new LogsCompositeBucketsRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("metadata.funnel_name"), 100, null, null);

        assertThatThrownBy(() -> queryBuilder.buildCompositeBucketsQuery(request))
            .isInstanceOf(AdminLogsException.class).satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void compositeBucketsRejectsSourceOutsideWhitelist() {
        LogsCompositeBucketsRequest request = new LogsCompositeBucketsRequest("biz-events", "", List.of(), ONE_DAY,
            List.of("metadata.funnel_name", "uuid"), 100, null, null);

        assertThatThrownBy(() -> queryBuilder.buildCompositeBucketsQuery(request))
            .isInstanceOf(AdminLogsException.class).satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void filteredMetricsBuildsTopLevelFilterPerGroup() {
        LogsFilteredMetricsRequest request = new LogsFilteredMetricsRequest("biz-events", "", List.of(), ONE_DAY,
            List.of(
                new LogsFilteredMetricGroup("lobby", "event_name:room_lobby_abandoned",
                    new LogsMetricSpec("avg", "metadata.wait_time_ms", "avg")),
                new LogsFilteredMetricGroup("noMetric", "event_name:result_share_abandoned", null)));

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildFilteredMetricsQuery(request);

        assertThat(built.body().at("/aggs/lobby/filter/query_string/query").asText())
            .isEqualTo("event_name:room_lobby_abandoned");
        assertThat(built.body().at("/aggs/lobby/aggs/metric/avg/field").asText()).isEqualTo("metadata.wait_time_ms");
        assertThat(built.body().at("/aggs/noMetric/filter/query_string/query").asText())
            .isEqualTo("event_name:result_share_abandoned");
        assertThat(built.body().at("/aggs/noMetric/aggs").isMissingNode()).isTrue();
    }

    @Test
    void filteredMetricsRejectsTooManyGroups() {
        LogsFilteredMetricGroup group = new LogsFilteredMetricGroup("g", "event_name:x", null);
        List<LogsFilteredMetricGroup> groups = List.of(group, group, group, group, group, group, group, group, group,
            group, group);
        LogsFilteredMetricsRequest request = new LogsFilteredMetricsRequest("biz-events", "", List.of(), ONE_DAY,
            groups);

        assertThatThrownBy(() -> queryBuilder.buildFilteredMetricsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void filteredMetricsRejectsBackslashInGroupQuery() {
        LogsFilteredMetricsRequest request = new LogsFilteredMetricsRequest("biz-events", "", List.of(), ONE_DAY,
            List.of(new LogsFilteredMetricGroup("bad", "event_name:\\foo", null)));

        assertThatThrownBy(() -> queryBuilder.buildFilteredMetricsQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    @Test
    void distinctCountBuildsCardinalityAgg() {
        LogsDistinctCountRequest request = new LogsDistinctCountRequest("biz-events", "", List.of(), ONE_DAY, "uuid",
            5_000);

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildDistinctCountQuery(request);

        assertThat(built.body().at("/aggs/distinct/cardinality/field").asText()).isEqualTo("uuid");
        assertThat(built.body().at("/aggs/distinct/cardinality/precision_threshold").asInt()).isEqualTo(5_000);
    }

    @Test
    void distinctCountDefaultsPrecisionThreshold() {
        LogsDistinctCountRequest request = new LogsDistinctCountRequest("biz-events", "", List.of(), ONE_DAY,
            "session_id", null);

        LogsQueryBuilder.BuiltLogsQuery built = queryBuilder.buildDistinctCountQuery(request);

        assertThat(built.body().at("/aggs/distinct/cardinality/precision_threshold").asInt()).isEqualTo(3_000);
    }

    @Test
    void distinctCountRejectsFieldOutsideWhitelist() {
        LogsDistinctCountRequest request = new LogsDistinctCountRequest("biz-events", "", List.of(), ONE_DAY, "service",
            null);

        assertThatThrownBy(() -> queryBuilder.buildDistinctCountQuery(request)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_FIELD));
    }

    @Test
    void distinctCountRejectsPrecisionOutOfRange() {
        LogsDistinctCountRequest tooLarge = new LogsDistinctCountRequest("biz-events", "", List.of(), ONE_DAY, "uuid",
            40_001);

        assertThatThrownBy(() -> queryBuilder.buildDistinctCountQuery(tooLarge)).isInstanceOf(AdminLogsException.class)
            .satisfies(
                error -> assertThat(((AdminLogsException) error).code()).isEqualTo(AdminLogsException.INVALID_QUERY));
    }

    private LogsSearchRequest searchRequest(String query, int size) {
        return new LogsSearchRequest("biz-events", query, List.of(), ONE_DAY, size, null);
    }
}
