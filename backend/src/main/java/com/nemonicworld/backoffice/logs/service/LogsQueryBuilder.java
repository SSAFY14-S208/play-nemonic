package com.nemonicworld.backoffice.logs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.backoffice.logs.config.AdminLogsProperties;
import com.nemonicworld.backoffice.logs.dto.request.LogsFieldSummaryRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsFilter;
import com.nemonicworld.backoffice.logs.dto.request.LogsHistogramRequest;
import com.nemonicworld.backoffice.logs.dto.request.LogsSearchRequest;
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

    private static final Duration MAX_TIME_RANGE = Duration.ofDays(30);
    private static final Pattern DANGEROUS_SCRIPT_PATTERN = Pattern.compile("(?i)\\bscript(?:\\b|_)");
    private static final Set<String> FORBIDDEN_DSL_KEYS = Set.of("script", "runtime_mappings", "script_fields",
        "_source_includes", "_source_excludes");
    private static final Set<String> FIELD_SUMMARY_FIELDS = Set.of("service", "event_name", "level", "content_type",
        "log_type", "room_id", "trace_id", "error.type");
    private static final Pattern FILTER_FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_@.]+");
    private static final Map<String, String> INDEX_PATTERNS = indexPatterns();

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
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", TRACK_TOTAL_HITS);
        body.set("query", buildQuery(request.query(), request.filters(), timeRange));
        body.set("aggs", buildHistogramAggregations(interval, timeRange));

        return new BuiltLogsQuery(indexPattern, body);
    }

    public BuiltMsearchQuery buildFieldSummaryQuery(LogsFieldSummaryRequest request) {
        String indexPattern = resolveIndexPattern(request.index());
        TimeRange timeRange = parseTimeRange(request.timeRange());
        List<String> fields = validateFieldSummaryFields(request.fields());
        StringBuilder body = new StringBuilder();
        for (String field : fields) {
            ObjectNode header = objectMapper.createObjectNode();
            header.put("index", indexPattern);
            ObjectNode search = objectMapper.createObjectNode();
            search.put("size", 0);
            search.put("track_total_hits", TRACK_TOTAL_HITS);
            search.set("query", buildQuery(request.query(), request.filters(), timeRange));
            search.set("aggs", buildTermsAggregation(field));

            body.append(header).append('\n').append(search).append('\n');
        }

        return new BuiltMsearchQuery(body.toString(), fields);
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

    private ObjectNode buildHistogramAggregations(String interval, TimeRange timeRange) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode ts = aggs.putObject("ts");
        ObjectNode dateHistogram = ts.putObject("date_histogram");
        dateHistogram.put("field", "@timestamp");
        dateHistogram.put("fixed_interval", interval);
        dateHistogram.put("min_doc_count", 0);
        ObjectNode bounds = dateHistogram.putObject("extended_bounds");
        bounds.put("min", timeRange.from().toString());
        bounds.put("max", timeRange.to().toString());
        ObjectNode level = ts.putObject("aggs").putObject("level").putObject("terms");
        level.put("field", "level");
        level.put("size", 10);
        level.put("missing", "UNKNOWN");

        return aggs;
    }

    private ObjectNode buildTermsAggregation(String field) {
        ObjectNode aggs = objectMapper.createObjectNode();
        ObjectNode terms = aggs.putObject("values").putObject("terms");
        terms.put("field", field);
        terms.put("size", 10);

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
