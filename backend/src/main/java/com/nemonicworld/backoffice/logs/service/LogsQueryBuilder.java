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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogsQueryBuilder {

    static final int DEFAULT_SEARCH_SIZE = 50;
    static final int TRACK_TOTAL_HITS = 10_000;

    private static final int DEFAULT_FIELD_SUMMARY_SIZE = 10;
    private static final int MAX_FIELD_SUMMARY_SIZE = 100;
    private static final int HISTOGRAM_GROUPBY_SIZE = 20;
    private static final int DEFAULT_TERMS_WITH_SUBS_SIZE = 10;
    private static final int MAX_TERMS_WITH_SUBS_SIZE = 50;
    private static final int MAX_TERMS_WITH_SUBS_SUBFILTERS = 5;
    private static final int DEFAULT_TERMS_WITH_METRIC_SIZE = 10;
    private static final int MAX_TERMS_WITH_METRIC_SIZE = 100;
    private static final int MAX_TERMS_WITH_METRIC_METRICS = 3;
    private static final int DEFAULT_COMPOSITE_SIZE = 100;
    private static final int MAX_COMPOSITE_SIZE = 500;
    private static final int MAX_COMPOSITE_SUB_AGGS = 3;
    private static final int COMPOSITE_SOURCES_REQUIRED = 2;
    private static final int MAX_FILTERED_METRICS_GROUPS = 10;
    private static final int DEFAULT_CARDINALITY_PRECISION = 3_000;
    private static final int MAX_CARDINALITY_PRECISION = 40_000;
    private static final String COMPOSITE_MISSING_BUCKET = "__missing__";

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(30);
    private static final Pattern DANGEROUS_SCRIPT_PATTERN = Pattern.compile("(?i)\\bscript(?:\\b|_)");
    private static final Pattern SUB_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Set<String> FORBIDDEN_DSL_KEYS = Set.of("script", "runtime_mappings", "script_fields",
        "_source_includes", "_source_excludes");
    private static final Set<String> FIELD_SUMMARY_FIELDS = Set.of("service", "event_name", "level", "content_type",
        "log_type", "room_id", "trace_id", "error.type", "metadata.funnel_name", "metadata.step_name",
        "metadata.entry_type", "metadata.referrer", "path", "prev_path");
    private static final Set<String> HISTOGRAM_GROUPBY_FIELDS = Set.of("level", "metadata.funnel_name",
        "metadata.entry_type", "event_name", "service");
    private static final Set<String> TERMS_GROUPBY_FIELDS = Set.of("metadata.funnel_name", "metadata.entry_type",
        "event_name", "path", "service");
    private static final Set<String> METRIC_NUMERIC_FIELDS = Set.of("metadata.time_on_page_ms",
        "metadata.time_visible_ms", "metadata.time_in_session_ms", "metadata.elapsed_ms", "metadata.wait_time_ms",
        "metadata.time_on_result_ms");
    private static final Set<String> COMPOSITE_SOURCE_FIELDS = Set.of("metadata.funnel_name", "metadata.step_name",
        "metadata.entry_type", "path", "prev_path", "event_name");
    private static final Set<String> COMPOSITE_SUB_AGG_FIELDS;
    private static final Set<String> CARDINALITY_FIELDS = Set.of("uuid", "session_id", "trace_id");
    private static final Set<String> METRIC_TYPES = Set.of("avg", "sum", "max", "min");
    private static final Pattern FILTER_FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_@.]+");
    private static final Map<String, String> INDEX_PATTERNS = indexPatterns();

    static {
        Set<String> compositeSubAggFields = new LinkedHashSet<>(METRIC_NUMERIC_FIELDS);
        compositeSubAggFields.add("metadata.step_index");
        COMPOSITE_SUB_AGG_FIELDS = Set.copyOf(compositeSubAggFields);
    }

    private final ObjectMapper objectMapper;
    private final AdminLogsProperties properties;

    public LogsQueryBuilder(ObjectMapper objectMapper, AdminLogsProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void validateRawRequest(JsonNode requestNode) {
        if (requestNode == null || !requestNode.isObject()) {
            throw AdminLogsException.invalidQuery();
        }

        validateNoForbiddenDslKeys(requestNode);
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
        int size = validateSearchSize(request.size());
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
        String groupBy = validateHistogramGroupBy(request.groupBy());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));
        body.set("aggs", buildHistogramAggregations(interval, timeRange, groupBy));

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltMsearchQuery buildFieldSummaryQuery(LogsFieldSummaryRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<String> fields = validateFieldSummaryFields(request.fields());
        int size = validateFieldSummarySize(request.size());
        StringBuilder body = new StringBuilder();
        for (String field : fields) {
            ObjectNode header = objectMapper.createObjectNode();
            header.put("index", indexPattern);
            ObjectNode search = objectMapper.createObjectNode();
            search.put("size", 0);
            search.put("track_total_hits", TRACK_TOTAL_HITS);
            search.set("query", buildQuery(request.query(), request.filters(), timeRange));
            search.set("aggs", buildTermsAggregation(field, size));

            body.append(header).append('\n').append(search).append('\n');
        }

        return new BuiltMsearchQuery(body.toString(), fields);
    }

    public BuiltLogsQuery buildTermsWithSubsQuery(LogsTermsWithSubsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String groupBy = validateTermsGroupBy(request.groupBy());
        int size = validateRange(request.size(), DEFAULT_TERMS_WITH_SUBS_SIZE, MAX_TERMS_WITH_SUBS_SIZE);
        List<LogsSubFilter> subFilters = validateSubFilters(request.subFilters());
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
            subAggs.set(sub.name(), buildFilterAgg(sub.query()));
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildTermsWithMetricQuery(LogsTermsWithMetricRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String groupBy = validateTermsGroupBy(request.groupBy());
        int size = validateRange(request.size(), DEFAULT_TERMS_WITH_METRIC_SIZE, MAX_TERMS_WITH_METRIC_SIZE);
        List<LogsMetricSpec> metrics = validateMetrics(request.metrics(), MAX_TERMS_WITH_METRIC_METRICS,
            METRIC_NUMERIC_FIELDS);
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
            subAggs.set(metric.name(), buildMetricAgg(metric));
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildCompositeBucketsQuery(LogsCompositeBucketsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<String> sources = validateCompositeSources(request.sources());
        int size = validateRange(request.size(), DEFAULT_COMPOSITE_SIZE, MAX_COMPOSITE_SIZE);
        List<LogsMetricSpec> subAggs = request.subAggs() == null || request.subAggs().isEmpty()
            ? List.of()
            : validateMetrics(request.subAggs(), MAX_COMPOSITE_SUB_AGGS, COMPOSITE_SUB_AGG_FIELDS);

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
                innerAggs.set(subAgg.name(), buildMetricAgg(subAgg));
            }
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildFilteredMetricsQuery(LogsFilteredMetricsRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<LogsFilteredMetricGroup> groups = validateFilteredMetricGroups(request.groups());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));

        ObjectNode aggsRoot = body.putObject("aggs");
        for (LogsFilteredMetricGroup group : groups) {
            ObjectNode groupAgg = aggsRoot.putObject(group.name());
            groupAgg.set("filter", buildFilterAgg(group.query()).get("filter"));
            if (group.metric() != null) {
                ObjectNode groupSubAggs = groupAgg.putObject("aggs");
                groupSubAggs.set("metric", buildMetricAgg(group.metric()));
            }
        }

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltLogsQuery buildDistinctCountQuery(LogsDistinctCountRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        String field = validateCardinalityField(request.field());
        int precisionThreshold = validateCardinalityPrecision(request.precisionThreshold());

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
        return COMPOSITE_MISSING_BUCKET;
    }

    private void validateNoForbiddenDslKeys(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (FORBIDDEN_DSL_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    throw AdminLogsException.invalidQuery();
                }
                validateNoForbiddenDslKeys(entry.getValue());
            });
            return;
        }

        if (node.isArray()) {
            node.forEach(this::validateNoForbiddenDslKeys);
        }
    }

    private int validateSearchSize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_SEARCH_SIZE : requestedSize;
        if (size < 1 || size > properties.resolvedMaxSize()) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    private int validateFieldSummarySize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_FIELD_SUMMARY_SIZE : requestedSize;
        if (size < 1 || size > MAX_FIELD_SUMMARY_SIZE) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    private int validateRange(Integer requestedSize, int defaultSize, int maxSize) {
        int size = requestedSize == null ? defaultSize : requestedSize;
        if (size < 1 || size > maxSize) {
            throw AdminLogsException.invalidQuery();
        }

        return size;
    }

    private int validateCardinalityPrecision(Integer requested) {
        int value = requested == null ? DEFAULT_CARDINALITY_PRECISION : requested;
        if (value < 1 || value > MAX_CARDINALITY_PRECISION) {
            throw AdminLogsException.invalidQuery();
        }

        return value;
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

    private List<String> validateFieldSummaryFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw AdminLogsException.invalidField();
        }

        LinkedHashSet<String> validatedFields = new LinkedHashSet<>();
        for (String field : fields) {
            if (!StringUtils.hasText(field) || !FIELD_SUMMARY_FIELDS.contains(field.trim())) {
                throw AdminLogsException.invalidField();
            }
            validatedFields.add(field.trim());
        }

        return List.copyOf(validatedFields);
    }

    private String validateHistogramGroupBy(String groupBy) {
        if (groupBy == null) {
            return null;
        }

        String trimmed = groupBy.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!HISTOGRAM_GROUPBY_FIELDS.contains(trimmed)) {
            throw AdminLogsException.invalidField();
        }

        return trimmed;
    }

    private String validateTermsGroupBy(String groupBy) {
        if (!StringUtils.hasText(groupBy) || !TERMS_GROUPBY_FIELDS.contains(groupBy.trim())) {
            throw AdminLogsException.invalidField();
        }

        return groupBy.trim();
    }

    private String validateCardinalityField(String field) {
        if (!StringUtils.hasText(field) || !CARDINALITY_FIELDS.contains(field.trim())) {
            throw AdminLogsException.invalidField();
        }

        return field.trim();
    }

    private List<String> validateCompositeSources(List<String> sources) {
        if (sources == null || sources.size() != COMPOSITE_SOURCES_REQUIRED) {
            throw AdminLogsException.invalidQuery();
        }
        for (String source : sources) {
            if (!StringUtils.hasText(source) || !COMPOSITE_SOURCE_FIELDS.contains(source.trim())) {
                throw AdminLogsException.invalidField();
            }
        }

        return sources.stream().map(String::trim).toList();
    }

    private List<LogsSubFilter> validateSubFilters(List<LogsSubFilter> subFilters) {
        if (subFilters == null || subFilters.isEmpty() || subFilters.size() > MAX_TERMS_WITH_SUBS_SUBFILTERS) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsSubFilter sub : subFilters) {
            if (sub == null || !StringUtils.hasText(sub.name()) || !SUB_NAME_PATTERN.matcher(sub.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(sub.name())) {
                throw AdminLogsException.invalidQuery();
            }
            validateNamedQueryString(sub.query());
        }

        return List.copyOf(subFilters);
    }

    private List<LogsMetricSpec> validateMetrics(List<LogsMetricSpec> metrics, int maxCount,
        Set<String> allowedFields) {
        if (metrics == null || metrics.isEmpty() || metrics.size() > maxCount) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsMetricSpec metric : metrics) {
            if (metric == null || !StringUtils.hasText(metric.name())
                || !SUB_NAME_PATTERN.matcher(metric.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(metric.name())) {
                throw AdminLogsException.invalidQuery();
            }
            if (!StringUtils.hasText(metric.type()) || !METRIC_TYPES.contains(metric.type().toLowerCase(Locale.ROOT))) {
                throw AdminLogsException.invalidQuery();
            }
            if (!StringUtils.hasText(metric.field()) || !allowedFields.contains(metric.field().trim())) {
                throw AdminLogsException.invalidField();
            }
        }

        return List.copyOf(metrics);
    }

    private List<LogsFilteredMetricGroup> validateFilteredMetricGroups(List<LogsFilteredMetricGroup> groups) {
        if (groups == null || groups.isEmpty() || groups.size() > MAX_FILTERED_METRICS_GROUPS) {
            throw AdminLogsException.invalidQuery();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (LogsFilteredMetricGroup group : groups) {
            if (group == null || !StringUtils.hasText(group.name())
                || !SUB_NAME_PATTERN.matcher(group.name()).matches()) {
                throw AdminLogsException.invalidQuery();
            }
            if (!names.add(group.name())) {
                throw AdminLogsException.invalidQuery();
            }
            validateNamedQueryString(group.query());
            LogsMetricSpec metric = group.metric();
            if (metric != null) {
                if (!StringUtils.hasText(metric.type())
                    || !METRIC_TYPES.contains(metric.type().toLowerCase(Locale.ROOT))) {
                    throw AdminLogsException.invalidQuery();
                }
                if (!StringUtils.hasText(metric.field()) || !METRIC_NUMERIC_FIELDS.contains(metric.field().trim())) {
                    throw AdminLogsException.invalidField();
                }
            }
        }

        return List.copyOf(groups);
    }

    private void validateNamedQueryString(String query) {
        if (!StringUtils.hasText(query)) {
            throw AdminLogsException.invalidQuery();
        }
        validateQueryString(query.trim());
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

        validateQueryString(query);
        ObjectNode queryString = objectMapper.createObjectNode();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        queryString.set("query_string", body);

        return queryString;
    }

    private ObjectNode buildFilterAgg(String rawQuery) {
        validateNamedQueryString(rawQuery);
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode filter = wrapper.putObject("filter");
        ObjectNode queryString = filter.putObject("query_string");
        queryString.put("query", rawQuery.trim());

        return wrapper;
    }

    private ObjectNode buildMetricAgg(LogsMetricSpec metric) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode agg = wrapper.putObject(metric.type().toLowerCase(Locale.ROOT));
        agg.put("field", metric.field().trim());

        return wrapper;
    }

    private void validateQueryString(String query) {
        if (DANGEROUS_SCRIPT_PATTERN.matcher(query).find() || query.contains("\\")) {
            throw AdminLogsException.invalidQuery();
        }
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
        if (!FILTER_FIELD_PATTERN.matcher(field).matches()) {
            throw AdminLogsException.invalidField();
        }

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

    private ObjectNode buildHistogramAggregations(String interval, TimeRange timeRange, String groupBy) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode ts = aggs.putObject("ts");
        ObjectNode dateHistogram = ts.putObject("date_histogram");
        dateHistogram.put("field", "@timestamp");
        dateHistogram.put("fixed_interval", interval);
        dateHistogram.put("min_doc_count", 0);
        ObjectNode bounds = dateHistogram.putObject("extended_bounds");
        bounds.put("min", timeRange.from().toString());
        bounds.put("max", timeRange.to().toString());
        ObjectNode subAggs = ts.putObject("aggs");
        ObjectNode level = subAggs.putObject("level").putObject("terms");
        level.put("field", "level");
        level.put("size", 10);
        level.put("missing", "UNKNOWN");

        if (groupBy != null) {
            ObjectNode byField = subAggs.putObject("by_field").putObject("terms");
            byField.put("field", groupBy);
            byField.put("size", HISTOGRAM_GROUPBY_SIZE);
            byField.put("missing", "_missing");
        }

        return aggs;
    }

    private ObjectNode buildTermsAggregation(String field, int size) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode terms = aggs.putObject("values").putObject("terms");
        terms.put("field", field);
        terms.put("size", size);

        return aggs;
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
