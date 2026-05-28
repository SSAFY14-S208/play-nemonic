package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.nemonicworld.backoffice.logs.service.support.LogsAggregationBuilder;
import com.nemonicworld.backoffice.logs.service.support.LogsQueryValidationSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogsQueryBuilder {

    static final int TRACK_TOTAL_HITS = 10_000;

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(30);
    private static final Map<String, String> INDEX_PATTERNS = indexPatterns();

    private final ObjectMapper objectMapper;
    private final LogsQueryValidationSupport validationSupport;
    private final LogsAggregationBuilder aggregationBuilder;

    @Autowired
    public LogsQueryBuilder(ObjectMapper objectMapper, LogsQueryValidationSupport validationSupport,
        LogsAggregationBuilder aggregationBuilder) {
        this.objectMapper = objectMapper;
        this.validationSupport = validationSupport;
        this.aggregationBuilder = aggregationBuilder;
    }

    LogsQueryBuilder(ObjectMapper objectMapper, AdminLogsProperties properties) {
        LogsQueryValidationSupport validationSupport = new LogsQueryValidationSupport(properties);
        this.objectMapper = objectMapper;
        this.validationSupport = validationSupport;
        this.aggregationBuilder = new LogsAggregationBuilder(objectMapper, validationSupport);
    }

    public void validateRawRequest(JsonNode requestNode) {
        validationSupport.validateRawRequest(requestNode);
    }

    public String resolveIndexPattern(String index) {
        if (!StringUtils.hasText(index)) {
            throw AdminLogsException.invalidIndex();
        }

        String pattern = INDEX_PATTERNS.get(index.trim());
        if (pattern == null) {
            throw AdminLogsException.invalidIndex();
        }

        return pattern;
    }

    public BuiltLogsQuery buildSearchQuery(LogsSearchRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        int size = validationSupport.validateSearchSize(request.size());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("sort", buildSort());
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        if (request.searchAfter() != null) {
            if (request.searchAfter().size() != 2) {
                throw AdminLogsException.invalidQuery();
            }
            body.set("search_after", objectMapper.valueToTree(request.searchAfter()));
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildHistogramQuery(LogsHistogramRequest request, String interval) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String groupBy = validationSupport.validateHistogramGroupBy(request.groupBy());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));
        body.set("aggs",
            aggregationBuilder.buildHistogramAggregations(interval, timeRange.from(), timeRange.to(), groupBy));

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltMsearchQuery buildFieldSummaryQuery(LogsFieldSummaryRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<String> fields = validationSupport.validateFieldSummaryFields(request.fields());
        int size = validationSupport.validateFieldSummarySize(request.size());
        StringBuilder body = new StringBuilder();
        for (String field : fields) {
            ObjectNode header = objectMapper.createObjectNode();
            header.put("index", indexPattern);
            ObjectNode search = objectMapper.createObjectNode();
            search.put("size", 0);
            search.put("track_total_hits", TRACK_TOTAL_HITS);
            search.set("query", buildQuery(request.query(), request.filters(), timeRange));
            search.set("aggs", aggregationBuilder.buildTermsAggregation(field, size));

            body.append(header).append('\n').append(search).append('\n');
        }

        return new BuiltMsearchQuery(body.toString(), fields);
    }

    public BuiltLogsQuery buildTermsWithSubsQuery(LogsTermsWithSubsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String groupBy = validationSupport.validateTermsGroupBy(request.groupBy());
        int size = validationSupport.validateTermsWithSubsSize(request.size());
        List<LogsSubFilter> subFilters = validationSupport.validateSubFilters(request.subFilters());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggs = body.putObject("aggs");
        ObjectNode termsAgg = aggs.putObject("groups");
        ObjectNode terms = termsAgg.putObject("terms");
        terms.put("field", groupBy);
        terms.put("size", size);

        ObjectNode subAggs = termsAgg.putObject("aggs");
        for (LogsSubFilter sub : subFilters) {
            subAggs.set(sub.name(), aggregationBuilder.buildFilterAgg(sub.query()));
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildTermsWithMetricQuery(LogsTermsWithMetricRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String groupBy = validationSupport.validateTermsGroupBy(request.groupBy());
        int size = validationSupport.validateTermsWithMetricSize(request.size());
        List<LogsMetricSpec> metrics = validationSupport.validateTermsWithMetricMetrics(request.metrics());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggs = body.putObject("aggs");
        ObjectNode termsAgg = aggs.putObject("groups");
        ObjectNode terms = termsAgg.putObject("terms");
        terms.put("field", groupBy);
        terms.put("size", size);
        String primaryMetricName = metrics.get(0).name();
        ObjectNode order = terms.putObject("order");
        order.put(primaryMetricName, "desc");

        ObjectNode subAggs = termsAgg.putObject("aggs");
        for (LogsMetricSpec metric : metrics) {
            subAggs.set(metric.name(), aggregationBuilder.buildMetricAgg(metric));
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildCompositeBucketsQuery(LogsCompositeBucketsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<String> sources = validationSupport.validateCompositeSources(request.sources());
        int size = validationSupport.validateCompositeSize(request.size());
        List<LogsMetricSpec> subAggs = request.subAggs() == null || request.subAggs().isEmpty()
            ? List.of()
            : validationSupport.validateCompositeSubAggs(request.subAggs());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggsRoot = body.putObject("aggs");
        ObjectNode compositeAgg = aggsRoot.putObject("composite_buckets");
        ObjectNode composite = compositeAgg.putObject("composite");
        composite.put("size", size);
        ArrayNode sourcesArray = composite.putArray("sources");
        for (String source : sources) {
            ObjectNode entry = objectMapper.createObjectNode();
            ObjectNode termsWrapper = entry.putObject(source);
            ObjectNode terms = termsWrapper.putObject("terms");
            terms.put("field", source);
            terms.put("missing_bucket", true);
            sourcesArray.add(entry);
        }
        if (request.after() != null && !request.after().isNull()) {
            if (!request.after().isObject()) {
                throw AdminLogsException.invalidQuery();
            }
            composite.set("after", request.after());
        }

        if (!subAggs.isEmpty()) {
            ObjectNode innerAggs = compositeAgg.putObject("aggs");
            for (LogsMetricSpec subAgg : subAggs) {
                innerAggs.set(subAgg.name(), aggregationBuilder.buildMetricAgg(subAgg));
            }
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildFilteredMetricsQuery(LogsFilteredMetricsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<LogsFilteredMetricGroup> groups = validationSupport.validateFilteredMetricGroups(request.groups());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggsRoot = body.putObject("aggs");
        for (LogsFilteredMetricGroup group : groups) {
            ObjectNode groupAgg = aggsRoot.putObject(group.name());
            groupAgg.set("filter", aggregationBuilder.buildFilterAgg(group.query()).get("filter"));
            if (group.metric() != null) {
                ObjectNode groupSubAggs = groupAgg.putObject("aggs");
                groupSubAggs.set("metric", aggregationBuilder.buildMetricAgg(group.metric()));
            }
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildDistinctCountQuery(LogsDistinctCountRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String field = validationSupport.validateCardinalityField(request.field());
        int precisionThreshold = validationSupport.validateCardinalityPrecision(request.precisionThreshold());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggs = body.putObject("aggs");
        ObjectNode distinct = aggs.putObject("distinct").putObject("cardinality");
        distinct.put("field", field);
        distinct.put("precision_threshold", precisionThreshold);

        return new BuiltLogsQuery(indexPattern, body);
    }

    public String chooseHistogramInterval(LogsTimeRange requestTimeRange) {
        TimeRange timeRange = parseTimeRange(requestTimeRange);
        Duration duration = Duration.between(timeRange.from(), timeRange.to());
        if (duration.compareTo(Duration.ofHours(1)) <= 0) {
            return "30s";
        }
        if (duration.compareTo(Duration.ofHours(6)) <= 0) {
            return "1m";
        }
        if (duration.compareTo(Duration.ofDays(1)) <= 0) {
            return "5m";
        }
        if (duration.compareTo(Duration.ofDays(7)) <= 0) {
            return "1h";
        }

        return "1d";
    }

    public String compositeMissingBucketSentinel() {
        return LogsAggregationBuilder.COMPOSITE_MISSING_BUCKET;
    }

    private TimeRange parseTimeRange(LogsTimeRange timeRange) {
        if (timeRange == null || !StringUtils.hasText(timeRange.from()) || !StringUtils.hasText(timeRange.to())) {
            throw AdminLogsException.invalidTimeRange();
        }

        Instant from;
        Instant to;
        try {
            from = Instant.parse(timeRange.from());
            to = Instant.parse(timeRange.to());
        } catch (DateTimeParseException e) {
            throw AdminLogsException.invalidTimeRange();
        }

        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_TIME_RANGE) > 0) {
            throw AdminLogsException.invalidTimeRange();
        }

        return new TimeRange(from, to);
    }

    private ObjectNode buildQuery(String rawQuery, List<LogsFilter> requestFilters, TimeRange timeRange) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode bool = query.putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.add(buildMustQuery(rawQuery));
        ArrayNode filters = bool.putArray("filter");
        filters.add(buildTimeRangeFilter(timeRange));

        List<JsonNode> mustNot = new ArrayList<>();
        for (LogsFilter filter : safeFilters(requestFilters)) {
            JsonNode term = buildTermFilter(filter);
            if (filter.negated()) {
                mustNot.add(term);
            } else {
                filters.add(term);
            }
        }

        if (!mustNot.isEmpty()) {
            ArrayNode mustNotNode = bool.putArray("must_not");
            mustNot.forEach(mustNotNode::add);
        }

        return query;
    }

    private JsonNode buildMustQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (!StringUtils.hasText(query)) {
            ObjectNode matchAll = objectMapper.createObjectNode();
            matchAll.set("match_all", objectMapper.createObjectNode());
            return matchAll;
        }

        validationSupport.validateQueryString(query);
        ObjectNode queryString = objectMapper.createObjectNode();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        queryString.set("query_string", body);

        return queryString;
    }

    private JsonNode buildTimeRangeFilter(TimeRange timeRange) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode timestamp = wrapper.putObject("range").putObject("@timestamp");
        timestamp.put("gte", timeRange.from().toString());
        timestamp.put("lte", timeRange.to().toString());

        return wrapper;
    }

    private List<LogsFilter> safeFilters(List<LogsFilter> filters) {
        return filters == null ? List.of() : filters;
    }

    private JsonNode buildTermFilter(LogsFilter filter) {
        if (filter == null || !StringUtils.hasText(filter.field()) || filter.value() == null
            || filter.value() instanceof Map<?, ?> || filter.value() instanceof Iterable<?>) {
            throw AdminLogsException.invalidQuery();
        }

        String field = filter.field().trim();
        validationSupport.validateFilterFieldName(field);

        ObjectNode fieldBody = objectMapper.createObjectNode();
        fieldBody.set(field, objectMapper.valueToTree(filter.value()));
        ObjectNode term = objectMapper.createObjectNode();
        term.set("term", fieldBody);

        return term;
    }

    private ArrayNode buildSort() {
        ArrayNode sort = objectMapper.createArrayNode();
        ObjectNode timestampSort = objectMapper.createObjectNode();
        timestampSort.putObject("@timestamp").put("order", "desc");
        ObjectNode idSort = objectMapper.createObjectNode();
        idSort.putObject("_id").put("order", "asc");
        sort.add(timestampSort);
        sort.add(idSort);

        return sort;
    }

    private static Map<String, String> indexPatterns() {
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("biz-events", "biz-events-*");
        patterns.put("error-logs", "error-logs-*");
        patterns.put("access-logs", "access-logs-*");
        patterns.put("system-logs", "system-logs-*");
        patterns.put("audit-logs", "audit-logs-*");
        patterns.put("all", "biz-events-*,error-logs-*,access-logs-*,system-logs-*,audit-logs-*");

        return Map.copyOf(patterns);
    }

    public record BuiltLogsQuery(String indexPattern, ObjectNode body) {
    }

    public record BuiltMsearchQuery(String body, List<String> fields) {
    }

    private record TimeRange(Instant from, Instant to) {
    }
}
